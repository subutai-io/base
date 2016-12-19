package io.subutai.core.hubmanager.impl;


import java.io.IOException;

import javax.ws.rs.core.Form;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.HttpStatus;

import io.subutai.common.security.crypto.pgp.PGPEncryptionUtil;
import io.subutai.common.settings.SubutaiInfo;
import io.subutai.core.hubmanager.api.exception.HubManagerException;
import io.subutai.core.hubmanager.api.model.Config;
import io.subutai.core.hubmanager.impl.http.HubRestClient;
import io.subutai.core.hubmanager.api.RestResult;
import io.subutai.core.hubmanager.impl.model.ConfigEntity;
import io.subutai.core.identity.api.model.UserToken;
import io.subutai.hub.share.dto.PeerInfoDto;
import io.subutai.hub.share.dto.RegistrationDto;
import io.subutai.hub.share.pgp.key.PGPKeyHelper;

import static java.lang.String.format;


//TODO update peer name from Hub periodically since it can change on Hub side
public class RegistrationManager
{
    private final Logger log = LoggerFactory.getLogger( getClass() );

    private final HubManagerImpl hubManager;

    private final ConfigManager configManager;

    private final String hubIp;

    private final String peerId;

    private final HubRestClient restClient;


    public RegistrationManager( HubManagerImpl hubManager, ConfigManager configManager, String hupIp )
    {
        this.hubManager = hubManager;
        this.configManager = configManager;
        this.hubIp = hupIp;
        this.peerId = configManager.getPeerId();

        restClient = new HubRestClient( configManager );
    }


    public void registerPeer( String email, String password, String peerName ) throws HubManagerException
    {
        registerPeerPubKey();

        register( email, password, peerName );
    }


    private String readKeyText( PGPPublicKey key ) throws HubManagerException
    {
        try
        {
            return PGPEncryptionUtil.armorByteArrayToString( key.getEncoded() );
        }
        catch ( PGPException | IOException e )
        {
            throw new HubManagerException( "Error to read PGP key as text", e );
        }
    }


    private void registerPeerPubKey() throws HubManagerException
    {
        log.info( "Registering peer public key with Hub..." );

        Form form = new Form( "keytext", readKeyText( configManager.getPeerPublicKey() ) );

        RestResult<Object> restResult = restClient.postPlain( "/pks/add", form );

        if ( !restResult.isSuccess() )
        {
            throw new HubManagerException( "Error registering peer public key with Hub: " + restResult.getError() );
        }

        log.info( "Public key successfully registered" );
    }


    private RegistrationDto getRegistrationDto( String email, String password, String peerName )
    {
        PeerInfoDto peerInfoDto = new PeerInfoDto();

        peerInfoDto.setId( configManager.getPeerId() );
        peerInfoDto.setVersion( String.valueOf( SubutaiInfo.getVersion() ) );
        peerInfoDto.setName( peerName );

        RegistrationDto dto = new RegistrationDto( PGPKeyHelper.getFingerprint( configManager.getOwnerPublicKey() ) );

        dto.setOwnerEmail( email );
        dto.setOwnerPassword( password );
        dto.setPeerInfo( peerInfoDto );
        dto.setTemp1( configManager.getActiveUser().getFingerprint() );

        UserToken token = configManager.getPermanentToken();

        dto.setToken( token.getFullToken() );
        dto.setTokenId( configManager.getActiveUser().getAuthId() );

        return dto;
    }


    private void register( String email, String password, String peerName ) throws HubManagerException
    {
        log.info( "Registering peer with Hub..." );

        String path = format( "/rest/v1/peers/%s", peerId );

        RegistrationDto regDto = getRegistrationDto( email, password, peerName );

        RestResult<Object> restResult = restClient.post( path, regDto );

        if ( !restResult.isSuccess() )
        {
            throw new HubManagerException( "Error registering peer: " + restResult.getError() );
        }


        Config config;
        try
        {
            config = new ConfigEntity( regDto.getPeerInfo().getId(), hubIp, hubManager.getPeerInfo().get( "OwnerId" ),
                    email, peerName );
        }
        catch ( Exception e )
        {
            throw new HubManagerException( e );
        }

        hubManager.getConfigDataService().saveHubConfig( config );

        log.info( "Peer registered successfully" );
    }


    public void unregister() throws HubManagerException
    {
        log.info( "Unregistering peer..." );

        String path = format( "/rest/v1/peers/%s/delete", peerId );

        RestResult<Object> restResult = restClient.delete( path );

        if ( restResult.getStatus() == HttpStatus.SC_FORBIDDEN )
        {
            log.info( "Peer or its pubic key not found on Hub. Unregistered anyway." );
        }
        else if ( !restResult.isSuccess() )
        {
            throw new HubManagerException( "Error to unregister peer: " + restResult.getError() );
        }

        hubManager.getConfigDataService().deleteConfig( configManager.getPeerId() );

        log.info( "Unregistered successfully" );
    }
}
