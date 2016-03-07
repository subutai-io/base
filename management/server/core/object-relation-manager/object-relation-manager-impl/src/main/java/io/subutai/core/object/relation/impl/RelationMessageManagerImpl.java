package io.subutai.core.object.relation.impl;


import java.io.UnsupportedEncodingException;

import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.util.JsonUtil;
import io.subutai.core.object.relation.api.RelationMessageManager;
import io.subutai.core.object.relation.api.RelationVerificationException;
import io.subutai.core.object.relation.api.model.Relation;
import io.subutai.core.object.relation.impl.model.RelationImpl;
import io.subutai.core.security.api.SecurityManager;
import io.subutai.core.security.api.crypto.EncryptionTool;
import io.subutai.core.security.api.crypto.KeyManager;


public class RelationMessageManagerImpl implements RelationMessageManager
{
    private static final Logger logger = LoggerFactory.getLogger( RelationMessageManagerImpl.class );
    private SecurityManager securityManager;


    public RelationMessageManagerImpl( final SecurityManager securityManager )
    {
        this.securityManager = securityManager;
    }


    @Override
    public Relation decryptAndVerifyMessage( final String signedMessage, final String secretKeyId )
            throws UnsupportedEncodingException, RelationVerificationException
    {
        try
        {
            KeyManager keyManager = securityManager.getKeyManager();
            EncryptionTool encryptionTool = securityManager.getEncryptionTool();

            PGPSecretKeyRing secretKeyRing = keyManager.getSecretKeyRing( secretKeyId );

            byte[] extractedText = encryptionTool.extractClearSignContent( signedMessage.getBytes() );
            byte[] decrypted = encryptionTool.decrypt( extractedText, secretKeyRing, "" );

            String decryptedMessage = new String( decrypted, "UTF-8" );
            RelationImpl relation = JsonUtil.fromJson( decryptedMessage, RelationImpl.class );

            PGPPublicKeyRing publicKey = keyManager.getPublicKeyRing( relation.getKeyId() );
            if ( publicKey == null || !encryptionTool.verifyClearSign( signedMessage.getBytes(), publicKey ) )
            {
                throw new RelationVerificationException( "Relation message verification failed." );
            }

            return relation;
        }
        catch ( Exception ex )
        {
            throw new RelationVerificationException( "Relation verification failed.", ex );
        }
    }


    @Override
    public String authenticateSource( final Relation trustMessage )
    {
        return "";
    }


    @Override
    public boolean verifyMessageSource( final Relation trustMessage, final String signature, String sourceFingerprint )
    {
        return false;
    }
}
