package io.subutai.core.peer.impl;


import java.util.Date;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.base.Preconditions;

import io.subutai.common.environment.Containers;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.network.NetworkResourceImpl;
import io.subutai.common.network.UsedNetworkResources;
import io.subutai.common.peer.AlertEvent;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerInfo;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.protocol.P2pIps;
import io.subutai.common.protocol.ReverseProxyConfig;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.resource.HistoricalMetrics;
import io.subutai.common.resource.PeerResources;
import io.subutai.common.security.PublicKeyContainer;
import io.subutai.common.security.WebClientBuilder;
import io.subutai.common.util.DateTimeParam;


/**
 * Peer REST client
 */
public class PeerWebClient
{
    private static final Logger LOG = LoggerFactory.getLogger( PeerWebClient.class );

    private final Object provider;
    private final PeerInfo peerInfo;


    public PeerWebClient( final PeerInfo peerInfo, final Object provider )
    {
        Preconditions.checkNotNull( peerInfo );
        Preconditions.checkNotNull( provider );

        this.peerInfo = peerInfo;
        this.provider = provider;
    }


    public PeerInfo getInfo() throws PeerException
    {
        String path = "/info";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider, 3000, 15000, 1 );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;
        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error getting peer info: %s", e.getMessage() ) );
        }

        return checkResponse( response, PeerInfo.class );
    }


    public PublicKeyContainer createEnvironmentKeyPair( EnvironmentId environmentId ) throws PeerException
    {
        String path = "/pek";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.post( environmentId );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error creating peer environment key: %s", e.getMessage() ) );
        }

        return checkResponse( response, PublicKeyContainer.class );
    }


    public void updateEnvironmentPubKey( PublicKeyContainer publicKeyContainer ) throws PeerException
    {
        String path = "/pek";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.put( publicKeyContainer );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error updating peer environment key: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public HostInterfaces getInterfaces() throws PeerException
    {
        String path = "/interfaces";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error getting interfaces: %s", e.getMessage() ) );
        }

        return checkResponse( response, HostInterfaces.class );
    }


    public void resetP2PSecretKey( final String p2pHash, final String newSecretKey, final long ttlSeconds )
            throws PeerException
    {
        String path = "/p2presetkey";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.post( new P2PCredentials( p2pHash, newSecretKey, ttlSeconds ) );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error resetting P2P secret key: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public void joinP2PSwarm( final P2PConfig config ) throws PeerException
    {
        String path = "/p2ptunnel";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.post( config );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error joining P2P swarm: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public void joinOrUpdateP2PSwarm( final P2PConfig config ) throws PeerException
    {
        String path = "/p2ptunnel";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.put( config );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error joining/updating P2P swarm: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public void cleanupEnvironment( final EnvironmentId environmentId ) throws PeerException
    {
        String path = String.format( "/cleanup/%s", environmentId.getId() );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.delete();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error cleaning up environment: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public ResourceHostMetrics getResourceHostMetrics() throws PeerException
    {
        String path = "/resources";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error getting rh metrics: %s", e.getMessage() ) );
        }

        return checkResponse( response, ResourceHostMetrics.class );
    }


    public void alert( final AlertEvent alert ) throws PeerException
    {
        String path = "/alert";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        try
        {
            Response response = client.post( alert );
            if ( Response.Status.ACCEPTED.getStatusCode() != response.getStatus() )
            {
                throw new PeerException( "Alert not accepted." );
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error on alert: %s", e.getMessage() ) );
        }
    }


    public HistoricalMetrics getHistoricalMetrics( final String hostName, final Date startTime, final Date endTime )
            throws PeerException
    {
        final DateTimeParam startParam = new DateTimeParam( startTime );
        final DateTimeParam endParam = new DateTimeParam( endTime );

        String path = String.format( "/hmetrics/%s/%s/%s/%s/%s", hostName, startParam.getDateString(),
                startParam.getTimeString(), endParam.getDateString(), endParam.getTimeString() );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException(
                    String.format( "Error on retrieving historical metrics from remote peer: %s", e.getMessage() ) );
        }

        return checkResponse( response, HistoricalMetrics.class );
    }


    public PeerResources getResourceLimits( final String peerId ) throws PeerException
    {
        String path = String.format( "/limits/%s", peerId );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider, 3000, 15000, 1 );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error on retrieving peer limits: %s", e.getMessage() ) );
        }

        return checkResponse( response, PeerResources.class );
    }


    public void setupTunnels( final P2pIps p2pIps, final String environmentId ) throws PeerException
    {

        String path = String.format( "/tunnels/%s", environmentId );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider, 3000, 60000, 1 );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.TEXT_PLAIN );

        Response response;

        try
        {
            response = client.post( p2pIps );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error setting up tunnels: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public void addPeerEnvironmentPubKey( final String keyId, final String pubKeyRing ) throws PeerException
    {

        String path = String.format( "/pek/add/%s", keyId );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider, 3000, 15000, 1 );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.post( pubKeyRing );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error adding PEK: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public Containers getEnvironmentContainers( final EnvironmentId environmentId ) throws PeerException
    {
        String path = String.format( "/containers/%s", environmentId.getId() );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );
        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error on obtaining environment containers: %s", e.getMessage() ) );
        }

        return checkResponse( response, Containers.class );
    }


    public UsedNetworkResources getUsedNetResources() throws PeerException
    {
        String path = "/netresources";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException(
                    String.format( "Error obtaining reserved network resources: %s", e.getMessage() ) );
        }

        return checkResponse( response, UsedNetworkResources.class );
    }


    public void reserveNetworkResource( final NetworkResourceImpl networkResource ) throws PeerException
    {
        String path = "/netresources";

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.type( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.post( networkResource );
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error reserving network resources: %s", e.getMessage() ) );
        }

        checkResponse( response );
    }


    public TemplateKurjun getTemplate( final String templateName ) throws PeerException
    {
        String path = String.format( "/template/%s/get", templateName );

        WebClient client = WebClientBuilder.buildPeerWebClient( peerInfo, path, provider );

        client.accept( MediaType.APPLICATION_JSON );

        Response response;

        try
        {
            response = client.get();
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( String.format( "Error obtaining template : %s", e.getMessage() ) );
        }

        return checkResponse( response, TemplateKurjun.class );
    }


    protected <T> T checkResponse( Response response, Class<T> clazz ) throws PeerException
    {

        checkResponse( response );

        return response.readEntity( clazz );
    }


    protected void checkResponse( Response response ) throws PeerException
    {
        if ( response != null && response.getStatus() == 500 )
        {
            throw new PeerException( response.readEntity( String.class ) );
        }
    }
}
