package io.subutai.core.security.impl.crypto;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.AccessControlException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import io.subutai.common.peer.PeerInfo;
import io.subutai.common.security.crypto.pgp.KeyPair;
import io.subutai.common.security.crypto.pgp.PGPEncryptionUtil;
import io.subutai.common.security.crypto.pgp.PGPKeyUtil;
import io.subutai.common.security.objects.KeyTrustLevel;
import io.subutai.common.security.objects.SecurityKeyType;
import io.subutai.common.settings.SystemSettings;
import io.subutai.common.util.DateUtil;
import io.subutai.common.util.RestUtil;
import io.subutai.core.keyserver.api.KeyServer;
import io.subutai.core.security.api.crypto.EncryptionTool;
import io.subutai.core.security.api.crypto.KeyManager;
import io.subutai.core.security.api.dao.SecurityDataService;
import io.subutai.core.security.api.model.SecretKeyStore;
import io.subutai.core.security.api.model.SecurityKey;
import io.subutai.core.security.api.model.SecurityKeyTrust;
import io.subutai.core.security.impl.model.SecurityKeyData;


/**
 * Implementation of KeyManager API
 *
 * todo use the same database for keys (currently keys are stored by both security manager and key manager)
 */
public class KeyManagerImpl implements KeyManager
{
    private static final Logger LOG = LoggerFactory.getLogger( KeyManagerImpl.class );

    private SecurityDataService securityDataService = null;
    private KeyServer keyServer = null;
    private SecurityKeyData keyData = null;
    private EncryptionTool encryptionTool = null;
    private static final String PEER_PUBLIC_KEY_FILE =
            System.getenv( "SUBUTAI_APP_KEYSTORE_PATH" ) + "/peer.public.key";
    private static final String PEER_PRIVATE_KEY_FILE =
            System.getenv( "SUBUTAI_APP_KEYSTORE_PATH" ) + "/peer.secret.key";


    /* *****************************
     *
     */
    public KeyManagerImpl( SecurityDataService securityDataService, KeyServer keyServer,
                           SecurityKeyData securityKeyData )
    {
        this.keyData = securityKeyData;
        this.securityDataService = securityDataService;
        this.keyServer = keyServer;

        init();
    }


    /* *****************************
     *
     */
    public void setEncryptionTool( final EncryptionTool encryptionTool )
    {
        this.encryptionTool = encryptionTool;
    }


    /* *****************************
     * TODO if peer pgp keys are absent (fingerprint file is missing or db is missing), then generate new ones and
     * just save fingerprint to file system and store keys in db.
     * Next time if fingerprint exists just use it to fetch peer pgp keys from database.
     * No need to store pgp keys in the file system
     */
    private void init()
    {

        try
        {
            InputStream peerSecStream = PGPEncryptionUtil.getFileInputStream( PEER_PRIVATE_KEY_FILE );
            InputStream peerPubStream = PGPEncryptionUtil.getFileInputStream( PEER_PUBLIC_KEY_FILE );

            PGPPublicKeyRing peerPubRing;
            PGPSecretKeyRing peerSecRing;

            if ( peerPubStream == null || peerSecStream == null )
            {
                KeyPair keyPair = PGPEncryptionUtil
                        .generateKeyPair( String.format( "subutai%d@subutai.io", DateUtil.getUnixTimestamp() ),
                                SystemSettings.getPeerSecretKeyringPwd(), true );

                Files.write( keyPair.getPubKeyring(), new File( PEER_PUBLIC_KEY_FILE ) );
                Files.write( keyPair.getSecKeyring(), new File( PEER_PRIVATE_KEY_FILE ) );

                peerPubRing = PGPKeyUtil.readPublicKeyRing( keyPair.getPubKeyring() );
                peerSecRing = PGPKeyUtil.readSecretKeyRing( keyPair.getSecKeyring() );
            }
            else
            {
                peerPubRing = PGPKeyUtil.readPublicKeyRing( peerPubStream );
                peerSecRing = PGPKeyUtil.readSecretKeyRing( peerSecStream );
            }

            String peerId = PGPKeyUtil.getFingerprint( peerPubRing.getPublicKey().getFingerprint() );

            keyData.setManHostId( peerId );

            saveSecretKeyRing( peerId, SecurityKeyType.PeerKey.getId(), peerSecRing );
            savePublicKeyRing( peerId, SecurityKeyType.PeerKey.getId(), peerPubRing );
            //************************************************************
            //************************************************************
        }
        catch ( Exception ex )
        {
            LOG.error( " **** Error creating Keypair for LocalPeer **** :" + ex.toString(), ex );
        }
    }


    /* ***************************************************************
     *
     */
    @Override
    public String getPeerId()
    {
        return keyData.getManHostId();
    }


    /* ***************************************************************
     *
     */
    @Override
    public void setPeerOwnerId( String id )
    {
        keyData.setPeerOwnerId( id );
    }


    /* ***************************************************************
     *
     */
    @Override
    public String getPeerOwnerId()
    {
        return keyData.getPeerOwnerId();
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing signKey( PGPSecretKeyRing sourceSecRing, PGPPublicKeyRing targetPubRing, int trustLevel )
    {
        try
        {
            String sigId = PGPKeyUtil.encodeNumericKeyId( targetPubRing.getPublicKey().getKeyID() );

            targetPubRing = encryptionTool.signPublicKey( targetPubRing, sigId, sourceSecRing.getSecretKey(), "" );
        }
        catch ( Exception ex )
        {
        }

        return targetPubRing;
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing signKey( String sourceFingerprint, String targetFingerprint, int trustLevel )
    {
        PGPPublicKeyRing targetPubRing = getPublicKeyRingByFingerprint( sourceFingerprint );
        PGPSecretKeyRing sourceSecRing = getSecretKeyRing( targetFingerprint );

        return signKey( sourceSecRing, targetPubRing, trustLevel );
    }


    /* ***************************************************************
     *
     */
    @Override
    public String signPublicKey( String sourceIdentityId, String keyText, int trustLevel )
    {
        String keyStr = "";

        try
        {
            PGPPublicKeyRing targetPubRing = PGPKeyUtil.readPublicKeyRing( keyText );
            PGPSecretKeyRing sourceSecRing = getSecretKeyRing( sourceIdentityId );

            targetPubRing = signKey( sourceSecRing, targetPubRing, trustLevel );
            keyStr = encryptionTool.armorByteArrayToString( targetPubRing.getEncoded() );
        }
        catch ( Exception ex )
        {
            LOG.error( "**** Error !!! Error signing key, IdentityId: " + sourceIdentityId, ex );
        }
        return keyStr;
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing setKeyTrust( PGPSecretKeyRing sourceSecRing, PGPPublicKeyRing targetPubRing,
                                         int trustLevel )
    {
        String sFingerprint = PGPKeyUtil.getFingerprint( sourceSecRing.getPublicKey().getFingerprint() );
        String tFingerprint = PGPKeyUtil.getFingerprint( targetPubRing.getPublicKey().getFingerprint() );

        SecurityKeyTrust keyTrust = securityDataService.getKeyTrustData( sFingerprint, tFingerprint );

        try
        {
            if ( keyTrust == null )
            {
                keyTrust = saveKeyTrustData( sFingerprint, tFingerprint, KeyTrustLevel.Never.getId() );
            }

            if ( trustLevel == KeyTrustLevel.Never.getId() )
            {
                //******************************************
                targetPubRing = removeSignature( sourceSecRing.getPublicKey(), targetPubRing );
                updatePublicKeyRing( targetPubRing );
                //******************************************
            }
            else
            {
                if ( keyTrust.getLevel() == KeyTrustLevel.Never.getId() )
                {
                    targetPubRing = signKey( sourceSecRing, targetPubRing, trustLevel );
                    updatePublicKeyRing( targetPubRing );
                }
            }

            keyTrust.setLevel( trustLevel );
            securityDataService.updateKeyTrustData( keyTrust );
        }
        catch ( Exception ex )
        {
            LOG.error( " **** Error!!! Error creating key trust:" + ex.toString(), ex );
        }

        return targetPubRing;
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing setKeyTrust( String sourceFingerprint, String targetFingerprint, int trustLevel )
    {
        PGPSecretKeyRing sourceSecRing = getSecretKeyRingByFingerprint( sourceFingerprint );
        PGPPublicKeyRing targetPubRing = getPublicKeyRingByFingerprint( targetFingerprint );

        return setKeyTrust( sourceSecRing, targetPubRing, trustLevel );
    }


    @Override
    public PGPPublicKeyRing setKeyTrust( final String sourceFingerprint, final String targetFingerprint,
                                         final String encryptedMessage ) throws PGPException
    {

        byte[] decrypted = encryptionTool.decrypt( encryptedMessage.getBytes() );

        try
        {
            String decryptedMessage = new String( decrypted, "UTF-8" );

            // TODO trust message signature verification should be verified on message separately from signature
            // since on client side primarily Object properties are set then serialized to JSON and signature is
            // generated from this string and set as additional Object property. Finally Object serialized
            // again to JSON with signature and encoded with recipient's (management's), and sent as trust message.
            //
            // trustMessage:
            // { [ ( "I Fully trust myself to manage this Environment") signed by User] encrypted by Peer PubKey}

            //            TrustMessage trustMessage = JsonUtil.fromJson( decryptedMessage, TrustMessage.class );
        }
        catch ( UnsupportedEncodingException e )
        {
            LOG.error( "Error converting byte array to string with UTF-8 format.", e );
        }
        return null;
    }


    /* ***************************************************************
     *
     */
    private boolean verifyTrustRelationValidity( SecurityKeyType sourceKeyType, SecurityKeyType targetKeyType )
    {
        switch ( sourceKeyType )
        {
            case ContainerHostKey:
                return false;
            case EnvironmentKey:
                return targetKeyType == SecurityKeyType.PeerEnvironmentKey;
            case ManagementHostKey:
                return targetKeyType == SecurityKeyType.ResourceHostKey;
            case PeerEnvironmentKey:
                return targetKeyType == SecurityKeyType.ContainerHostKey;
            case PeerKey:
                return targetKeyType == SecurityKeyType.ManagementHostKey;
            case PeerOwnerKey:
                return targetKeyType == SecurityKeyType.PeerKey;
            case ResourceHostKey:
                return targetKeyType == SecurityKeyType.ContainerHostKey;
            case UserKey:
                return targetKeyType == SecurityKeyType.EnvironmentKey;
        }
        return false;
    }


    /* ***************************************************************
     *
     */
    @Override
    public boolean verifySignature( String sourceFingerprint, String targetFingerprint )
    {
        PGPPublicKeyRing sourcePubRing = getPublicKeyRingByFingerprint( sourceFingerprint );
        PGPPublicKeyRing targetPubRing = getPublicKeyRingByFingerprint( targetFingerprint );

        return verifySignature( sourcePubRing, targetPubRing );
    }


    /* ***************************************************************
     *
     */
    @Override
    public boolean verifySignature( PGPPublicKeyRing sourcePubRing, PGPPublicKeyRing targetPubRing )
    {
        PGPPublicKey keyToVerifyWith = sourcePubRing.getPublicKey();
        PGPPublicKey keyToVerify = targetPubRing.getPublicKey();
        String sigId = PGPKeyUtil.encodeNumericKeyId( keyToVerify.getKeyID() );

        return encryptionTool.verifyPublicKey( keyToVerify, sigId, keyToVerifyWith );
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing removeSignature( String sourceFingerprint, String targetFingerprint )
    {
        PGPPublicKeyRing targetPubRing = getPublicKeyRingByFingerprint( targetFingerprint );
        PGPPublicKeyRing sourcePubRing = getPublicKeyRingByFingerprint( sourceFingerprint );
        PGPPublicKey sourcePublicKey = sourcePubRing.getPublicKey();

        return removeSignature( sourcePublicKey, targetPubRing );
    }


    /* ***************************************************************
     *
     */
    @Override
    public PGPPublicKeyRing removeSignature( PGPPublicKey sourcePublicKey, PGPPublicKeyRing targetPubRing )
    {
        return encryptionTool.removeSignature( sourcePublicKey, targetPubRing );
    }


    /* ***************************************************************
     *
     */
    @Override
    public SecurityKeyTrust getKeyTrustData( String sourceFingerprint, String targetFingerprint )
    {
        return securityDataService.getKeyTrustData( sourceFingerprint, targetFingerprint );
    }


    /* ***************************************************************
     *
     */
    @Override
    public List<SecurityKeyTrust> getKeyTrustData( final String sourceFingerprint )
    {
        return securityDataService.getKeyTrustData( sourceFingerprint );
    }


    /* ***************************************************************
     *
     */
    @Override
    public void removeKeyTrust( String sourceFingerprint )
    {
        try
        {
            securityDataService.removeKeyTrustData( sourceFingerprint );
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error!!! Error removing key trust:" + ex.toString(), ex );
        }
    }


    /* ***************************************************************
     *
     */
    @Override
    public void removeKeyAllTrustData( String fingerprint )
    {
        try
        {
            securityDataService.removeKeyAllTrustData( fingerprint );
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error!!! Error removing key trust:" + ex.toString(), ex );
        }
    }


    /* ***************************************************************
     *
     */
    @Override
    public void removeKeyTrustData( String sourceFingerprint, String targetFingerprint )
    {
        try
        {
            securityDataService.removeKeyTrustData( sourceFingerprint, targetFingerprint );
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error!!! Error removing key trust:" + ex.toString(), ex );
        }
    }


    /* ***************************************************************
     *
     */
    @Override
    public SecurityKeyTrust saveKeyTrustData( String sourceFingerprint, String targetFingerprint, int trustLevel )
    {
        SecurityKeyTrust keyTrustData = null;
        try
        {
            keyTrustData = securityDataService.saveKeyTrustData( sourceFingerprint, targetFingerprint, trustLevel );
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error!!! Error saving key trust:" + ex.toString(), ex );
        }

        return keyTrustData;
    }


    /* ***************************************************************
     *
     */
    @Override
    public void saveSecretKeyRing( String identityId, int type, PGPSecretKeyRing secretKeyRing )
    {
        try
        {
            PGPPublicKey publicKey = secretKeyRing.getPublicKey();

            if ( publicKey != null )
            {
                // Store secretKey
                String fingerprint = PGPKeyUtil.getFingerprint( publicKey.getFingerprint() );
                String pwd = keyData.getSecretKeyringPwd();

                //*******************
                securityDataService.saveSecretKeyData( fingerprint, secretKeyRing.getEncoded(), pwd, type );
                securityDataService.saveKeyData( identityId, fingerprint, "", type );
                //*******************
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error storing Public key:" + ex.toString(), ex );
        }
    }


    /* ***************************************************************
     *
     */
    @Override
    public void savePublicKeyRing( String identityId, int type, String keyringAsASCII )
    {
        try
        {
            PGPPublicKeyRing pgpPublicKeyRing = PGPKeyUtil.readPublicKeyRing( keyringAsASCII );

            if ( pgpPublicKeyRing != null )
            {
                savePublicKeyRing( identityId, type, pgpPublicKeyRing );
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error storing Public key:" + ex.toString(), ex );
        }
    }


    /* *****************************
     *
     */
    @Override
    public void savePublicKeyRing( String identityId, int type, PGPPublicKeyRing publicKeyRing )
    {
        try
        {
            PGPPublicKey publicKey = PGPKeyUtil.readPublicKey( publicKeyRing );

            if ( Strings.isNullOrEmpty( identityId ) )
            {
                identityId = keyData.getManHostId();
            }

            if ( publicKey != null )
            {
                // Store public key in the KeyServer
                keyServer.addPublicKey( publicKeyRing );

                //*************************
                String fingerprint = PGPKeyUtil.getFingerprint( publicKey.getFingerprint() );
                securityDataService.saveKeyData( identityId, "", fingerprint, type );
                //*************************
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error storing Public key:" + ex.toString(), ex );
        }
    }


    /* *****************************
     *
     */
    @Override
    public void removePublicKeyRing( String identityId )
    {
        try
        {
            if ( !Objects.equals( identityId, keyData.getManHostId() ) )
            {
                String fingerprint = getFingerprint( identityId );
                securityDataService.removeKeyData( identityId );
                securityDataService.removeKeyAllTrustData( fingerprint );
            }

            //Remove from KeyStore
            //Currently not supported
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error removing Public key:" + ex.toString(), ex );
        }
    }


    /* *****************************
     *
     */
    @Override
    public void removeSecretKeyRing( String identityId )
    {
        try
        {
            if ( !Objects.equals( identityId, keyData.getManHostId() ) )
            {
                SecurityKey keyIden = securityDataService.getKeyData( identityId );

                if ( keyIden != null )
                {
                    securityDataService.removeSecretKeyData( keyIden.getSecretKeyFingerprint() );
                    securityDataService.removeKeyData( identityId );
                    securityDataService.removeKeyAllTrustData( keyIden.getSecretKeyFingerprint() );
                }
            }
            else
            {
                throw new AccessControlException( " ***** Error!Management Keys cannot be removed ****" );
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ******** Error removing Secret key:" + ex.toString(), ex );
        }
    }


    /* *****************************
     *
     */
    @Override
    public SecurityKey getKeyData( String identityId )
    {
        SecurityKey keyIden = null;
        try
        {
            if ( Strings.isNullOrEmpty( identityId ) )
            {
                identityId = keyData.getManHostId();
            }
            keyIden = securityDataService.getKeyData( identityId );
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting security key data:" + ex.toString(), ex );
        }
        return keyIden;
    }


    /* *****************************
     *
     */
    @Override
    public SecurityKey getKeyDataByFingerprint( String fingerprint )
    {
        SecurityKey keyIden = null;
        try
        {
            keyIden = securityDataService.getKeyDataByFingerprint( fingerprint );
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting security key data:" + ex.toString(), ex );
        }
        return keyIden;
    }


    /* *****************************
     *
     */
    @Override
    public void removeKeyData( String identityId )
    {
        try
        {
            if ( !Objects.equals( identityId, keyData.getManHostId() ) )
            {
                String fingerprint = getFingerprint( identityId );

                securityDataService.removeKeyData( identityId );
                securityDataService.removeSecretKeyData( identityId );
                securityDataService.removeKeyAllTrustData( fingerprint );
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error removing security key:" + ex.toString(), ex );
        }
    }


    /* *****************************
     *
     */
    @Override
    public PGPPublicKey getPublicKey( String identityId )
    {
        PGPPublicKeyRing publicKeyRing;

        try
        {
            publicKeyRing = getPublicKeyRing( identityId );

            if ( publicKeyRing != null )
            {
                return PGPKeyUtil.readPublicKey( publicKeyRing );
            }
            else
            {
                LOG.info( "********* Public key not found with identityId:" + identityId );
                return null;
            }
        }
        catch ( PGPException e )
        {
            return null;
        }
    }


    /* *****************************
     *
     */
    @Override
    public PGPPublicKeyRing getPublicKeyRingByFingerprint( String fingerprint )
    {
        PGPPublicKeyRing pubKeyRing = null;
        try
        {
            byte[] keyData = keyServer.getPublicKeyByFingerprint( fingerprint ).getKeyData();

            if ( keyData != null )
            {
                return PGPKeyUtil.readPublicKeyRing( keyData );
            }
        }
        catch ( PGPException e )
        {
        }

        return pubKeyRing;
    }


    /* *****************************
     *
     */
    @Override
    public String getPublicKeyRingAsASCII( String identityId )
    {
        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            SecurityKey keyIden = securityDataService.getKeyData( identityId );

            if ( keyIden == null )
            {
                LOG.info( "********* Public key not found with identityId:" + identityId );
                return "";
            }
            {
                byte[] keyData = keyServer.getPublicKeyByFingerprint( keyIden.getPublicKeyFingerprint() ).getKeyData();

                return PGPEncryptionUtil.armorByteArrayToString( keyData );
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Public keyRing:" + ex.toString(), ex );
            return "";
        }
    }


    /* *****************************
     *
     */
    @Override
    public PGPPublicKeyRing getPublicKeyRing( String identityId )
    {
        PGPPublicKeyRing publicKeyRing;

        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            SecurityKey keyIden = securityDataService.getKeyData( identityId );

            if ( keyIden == null )
            {
                throw new NullPointerException( "***** Error! Key Identity not found." );
            }
            else
            {

                byte[] keyData = keyServer.getPublicKeyByFingerprint( keyIden.getPublicKeyFingerprint() ).getKeyData();

                publicKeyRing = PGPKeyUtil.readPublicKeyRing( keyData );

                return publicKeyRing;
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Public key:" + ex.toString() );
            return null;
        }
    }


    /* *****************************
     *
     */
    @Override
    public String getFingerprint( String identityId )
    {

        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            SecurityKey keyIden = securityDataService.getKeyData( identityId );
            return keyIden.getPublicKeyFingerprint();
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting public key by fingerprint: " + identityId );
            return null;
        }
    }


    /* *****************************
     *
     */
    @Override
    public PGPSecretKeyRing getSecretKeyRing( String identityId )
    {
        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            PGPSecretKeyRing secretKeyRing;
            SecurityKey keyIden = securityDataService.getKeyData( identityId );

            if ( keyIden == null )
            {
                LOG.info( " **** Identity Info not found for host:" + identityId );
                return null;
            }
            else
            {
                String fingerprint = keyIden.getSecretKeyFingerprint();
                secretKeyRing =
                        PGPKeyUtil.readSecretKeyRing( securityDataService.getSecretKeyData( fingerprint ).getData() );

                if ( secretKeyRing != null )
                {
                    return secretKeyRing;
                }
                else
                {
                    LOG.info( " **** Object not found with fprint:" + fingerprint );
                    return null;
                }
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " **** Error getting Secret key:" + ex.toString(), ex );
            return null;
        }
    }


    /* *****************************
     *
     */
    @Override
    public InputStream getSecretKeyRingInputStream( String identityId )
    {
        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            SecurityKey keyIden = securityDataService.getKeyData( identityId );

            if ( keyIden != null )
            {
                return PGPKeyUtil.readSecretKeyRingInputStream(
                        securityDataService.getSecretKeyData( keyIden.getSecretKeyFingerprint() ).getData() );
            }
            else
            {
                return null;
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Secret key:" + ex.toString(), ex );
            return null;
        }
    }


    /* *************************************************************
     *
     */
    @Override
    public PGPSecretKey getSecretKey( String identityId )
    {
        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            PGPSecretKeyRing secretKeyRing = getSecretKeyRing( identityId );

            if ( secretKeyRing != null )
            {
                return PGPKeyUtil.readSecretKey( secretKeyRing );
            }
            else
            {
                return null;
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Secret key:" + ex.toString(), ex );
            return null;
        }
    }


    /* ******************************************************
     *
     */
    @Override
    public PGPPrivateKey getPrivateKey( String identityId )
    {
        PGPPrivateKey privateKey;

        if ( Strings.isNullOrEmpty( identityId ) )
        {
            identityId = keyData.getManHostId();
        }

        try
        {
            PGPSecretKey secretKey = getSecretKey( identityId );

            if ( secretKey != null )
            {
                privateKey = PGPEncryptionUtil.getPrivateKey( secretKey, keyData.getSecretKeyringPwd() );

                return privateKey;
            }
            else
            {
                return null;
            }
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Private key:" + ex.toString(), ex );
            return null;
        }
    }


    /* *****************************
     *
     */
    @Override
    public void updatePublicKeyRing( final PGPPublicKeyRing publicKeyRing )
    {
        try
        {
            keyServer.updatePublicKey( publicKeyRing );
        }
        catch ( IOException | PGPException e )
        {
            LOG.warn( e.getMessage() );
        }
    }


    /* ******************************************************************
     *
     */
    @Override
    public PGPSecretKey getSecretKeyByFingerprint( String fingerprint )
    {
        PGPSecretKey secretKey = null;

        try
        {
            ByteArrayInputStream barIn =
                    new ByteArrayInputStream( securityDataService.getSecretKeyData( fingerprint ).getData() );

            secretKey = PGPEncryptionUtil.findSecretKeyByFingerprint( barIn, fingerprint );
        }
        catch ( Exception ex )
        {
            LOG.error( " ***** Error getting Secret key:" + ex.toString(), ex );
        }

        return secretKey;
    }


    /* ******************************************************************
     *
     */
    @Override
    public PGPSecretKeyRing getSecretKeyRingByFingerprint( String fingerprint )
    {
        try
        {
            SecretKeyStore secData = securityDataService.getSecretKeyData( fingerprint );

            if ( secData != null )
            {
                return PGPKeyUtil.readSecretKeyRing( secData.getData() );
            }
            else
            {
                return null;
            }
        }
        catch ( PGPException e )
        {
            return null;
        }
    }


    /* *****************************
      *
      */
    public SecurityKeyData getSecurityKeyData()
    {
        return keyData;
    }


    /* *****************************************
     *
     */
    @Override
    public KeyPair generateKeyPair( String identityId, boolean armored )
    {
        KeyPair keyPair;

        try
        {
            keyPair = PGPEncryptionUtil.generateKeyPair( identityId, keyData.getSecretKeyringPwd(), armored );
            return keyPair;
        }
        catch ( Exception ex )
        {
            return null;
        }
    }


    /* *****************************************
     *
     */
    @Override
    public void saveKeyPair( String identityId, int type, KeyPair keyPair )
    {
        try
        {
            saveSecretKeyRing( identityId, type, PGPKeyUtil.readSecretKeyRing( keyPair.getSecKeyring() ) );
            savePublicKeyRing( identityId, type, PGPKeyUtil.readPublicKeyRing( keyPair.getPubKeyring() ) );
        }
        catch ( Exception ignored )
        {

        }
    }


    /* *****************************************
     *
     */
    @Override
    public void removeKeyRings( String identityId )
    {
        try
        {
            if ( !Objects.equals( identityId, keyData.getManHostId() ) )
            {
                removeSecretKeyRing( identityId );
                removePublicKeyRing( identityId );
            }
            else
            {
                LOG.info( identityId + " Cannot be removed (possibly ManagementHost):" );
            }
        }
        catch ( Exception ignored )
        {

        }
    }


    /* *************************************************************
     * Get Public key and save it in the local KeyServer
     */
    @Override
    public PGPPublicKey getRemoteHostPublicKey( PeerInfo peerInfo )
    {
        try
        {
            PGPPublicKeyRing pubRing;


            pubRing = getPublicKeyRing( peerInfo.getId() );

            if ( pubRing == null ) // Get from HTTP
            {
                String baseUrl = String.format( "%s/rest/v1", peerInfo.getPublicUrl() );
                WebClient client = RestUtil.createTrustedWebClient( baseUrl, keyData.getJsonProvider() );
                client.type( MediaType.MULTIPART_FORM_DATA ).accept( MediaType.APPLICATION_JSON );

                Response response =
                        client.path( "security/keyman/getpublickeyring" ).query( "hostid", peerInfo.getId() ).get();

                if ( response.getStatus() == Response.Status.OK.getStatusCode() )
                {
                    String publicKeyring = response.readEntity( String.class );
                    savePublicKeyRing( peerInfo.getId(), ( short ) 3, publicKeyring );
                }
                return getPublicKey( peerInfo.getId() );
            }
            else
            {
                return PGPKeyUtil.readPublicKey( pubRing );
            }
        }
        catch ( Exception ex )
        {
            return null;
        }
    }


    @Override
    public PGPPublicKey getRemoteHostPublicKey( final String hostIdTarget )
    {
        try
        {
            PGPPublicKeyRing pubRing;

            pubRing = getPublicKeyRing( hostIdTarget );

            if ( pubRing != null )
            {
                return PGPKeyUtil.readPublicKey( pubRing );
            }
        }
        catch ( Exception ex )
        {
            // ignore
        }
        return null;
    }


    /* *************************************************************
     *
     */
    @Override
    public SecurityKey getKeyDetails( final String fingerprint )
    {
        SecurityKey securityKey = getKeyDataByFingerprint( fingerprint );

        if ( securityKey != null )
        {
            List<SecurityKeyTrust> keyTrustList = securityDataService.getKeyTrustData( fingerprint );
            securityKey.getTrustedKeys().clear();
            securityKey.getTrustedKeys().addAll( keyTrustList );
        }

        return securityKey;
    }


    /* *************************************************************
     *
     */
    @Override
    public SecurityKey getKeyTrustTree( String identityId )
    {
        SecurityKey securityKey = getKeyData( identityId );

        if ( securityKey != null )
        {
            securityKey = getKeyDetails( securityKey.getPublicKeyFingerprint() );

            for ( SecurityKeyTrust keyTrust : securityKey.getTrustedKeys() )
            {
                SecurityKey targetKey = getKeyTrustSubTree( keyTrust.getTargetFingerprint() );

                keyTrust.setTargetKey( targetKey );
            }
        }

        return securityKey;
    }


    /* *************************************************************
     *
     */
    private SecurityKey getKeyTrustSubTree( String fingerprint )
    {
        SecurityKey securityKey = getKeyDetails( fingerprint );

        if ( securityKey != null )
        {
            for ( SecurityKeyTrust keyTrust : securityKey.getTrustedKeys() )
            {
                SecurityKey targetKey = getKeyTrustSubTree( keyTrust.getTargetFingerprint() );

                keyTrust.setTargetKey( targetKey );
            }
        }

        return securityKey;
    }


    @Override
    public int getTrustLevel( String sourceFingerprint, String targetFingerprint )
    {
        List<SecurityKeyTrust> sourceTrusts = getKeyTrustData( sourceFingerprint );
        Set<String> sourceChainTrust = Sets.newHashSet();
        sourceChainTrust.add( sourceFingerprint );
        int trustLevel = buildTrustChainTrustLevel( sourceFingerprint, sourceChainTrust, targetFingerprint, false );

        if ( trustLevel != -1 )
        {
            return trustLevel;
        }

        Set<String> targetChainTrust = Sets.newHashSet();
        sourceChainTrust.add( targetFingerprint );
        trustLevel = buildTrustChainTrustLevel( targetFingerprint, sourceChainTrust, sourceFingerprint, true );

        if ( trustLevel != -1 )
        {
            return trustLevel;
        }
        return KeyTrustLevel.Never.getId();
    }


    private int buildTrustChainTrustLevel( String source, Set<String> chainFingerprints, String target,
                                           boolean repeated )
    {
        List<SecurityKeyTrust> sourceTrusts = getKeyTrustData( source );
        for ( final SecurityKeyTrust sourceTrust : sourceTrusts )
        {
            if ( sourceTrust.getTargetFingerprint().equals( target ) )
            {
                return sourceTrust.getLevel();
            }
            // Before getting deeper into trust chain, check that next trust step is valid, otherwise stop looking
            // for any further connections. Another condition to eliminate infinite recursion by each next step
            // register id into simple fingerprints registry chainFingerprints
            if ( sourceTrust.getLevel() != KeyTrustLevel.Never.getId() )
            {
                // When looking for second iteration for chained trusts, concurrently pay attention to marginal trust
                if ( chainFingerprints.contains( sourceTrust.getTargetFingerprint() ) )
                {
                    if ( repeated )
                    {
                        return KeyTrustLevel.Marginal.getId();
                    }
                    continue;
                }
                chainFingerprints.add( sourceTrust.getTargetFingerprint() );
                int trustLevel =
                        buildTrustChainTrustLevel( sourceTrust.getTargetFingerprint(), chainFingerprints, target,
                                repeated );
                if ( trustLevel != -1 )
                {
                    return trustLevel;
                }
            }
        }
        return -1;
    }
}
