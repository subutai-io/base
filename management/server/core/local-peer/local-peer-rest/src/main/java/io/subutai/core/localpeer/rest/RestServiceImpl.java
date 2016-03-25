package io.subutai.core.localpeer.rest;


import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.subutai.common.host.HostId;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.network.Gateways;
import io.subutai.common.network.Vni;
import io.subutai.common.network.Vnis;
import io.subutai.common.peer.AlertEvent;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerInfo;
import io.subutai.common.protocol.ControlNetworkConfig;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.security.PublicKeyContainer;
import io.subutai.common.security.crypto.pgp.PGPKeyUtil;
import io.subutai.common.util.DateTimeParam;
import io.subutai.common.util.JsonUtil;
import io.subutai.common.util.RestUtil;


public class RestServiceImpl implements RestService
{
    private static final Logger LOGGER = LoggerFactory.getLogger( RestServiceImpl.class );
    private final LocalPeer localPeer;
    protected JsonUtil jsonUtil = new JsonUtil();
    protected RestUtil restUtil = new RestUtil();


    public RestServiceImpl( final LocalPeer localPeer )
    {
        this.localPeer = localPeer;
    }


    @Override
    public Response getLocalPeerInfo()
    {
        try
        {
            PeerInfo selfInfo = localPeer.getPeerInfo();
            return Response.ok( selfInfo ).build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public PeerInfo getPeerInfo() throws PeerException
    {
        return localPeer.getPeerInfo();
    }


    protected String getRequestIp()
    {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        HttpServletRequest request = ( HttpServletRequest ) message.get( AbstractHTTPDestination.HTTP_REQUEST );
        return request.getRemoteAddr();
    }


    @Override
    public Response getTemplate( final String templateName )
    {
        try
        {
            TemplateKurjun result = localPeer.getTemplate( templateName );
            return Response.ok( result ).build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response setDefaultGateway( final String containerId, final String gatewayIp )
    {
        try
        {
            Preconditions.checkArgument( !Strings.isNullOrEmpty( containerId ) );

            localPeer.getContainerHostById( containerId ).setDefaultGateway( gatewayIp );
            return Response.ok().build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getContainerHostInfoById( final String containerId )
    {
        try
        {
            Preconditions.checkArgument( !Strings.isNullOrEmpty( containerId ) );

            return Response.ok( jsonUtil.to( localPeer.getContainerHostInfoById( containerId ) ) ).build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getEnvironmentContainers( final EnvironmentId environmentId )
    {
        try
        {
            Preconditions.checkNotNull( environmentId );

            return Response.ok( localPeer.getEnvironmentContainers( environmentId ) ).build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Vnis getReservedVnis()
    {
        try
        {
            return localPeer.getReservedVnis();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Gateways getGateways()
    {
        try
        {
            return localPeer.getGateways();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public PublicKeyContainer createEnvironmentKeyPair( final EnvironmentId environmentId )
    {
        Preconditions.checkNotNull( environmentId );

        try
        {
            return localPeer.createPeerEnvironmentKeyPair( environmentId );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void updateEnvironmentKey( final PublicKeyContainer publicKeyContainer )
    {
        Preconditions.checkNotNull( publicKeyContainer );
        Preconditions.checkNotNull( publicKeyContainer.getKey() );
        Preconditions.checkNotNull( publicKeyContainer.getHostId() );
        Preconditions.checkNotNull( publicKeyContainer.getFingerprint() );

        try
        {
            final PGPPublicKeyRing pubKeyRing = PGPKeyUtil.readPublicKeyRing( publicKeyContainer.getKey() );
            localPeer.updatePeerEnvironmentPubKey( new EnvironmentId( publicKeyContainer.getHostId() ), pubKeyRing );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void removeEnvironmentKeyPair( final EnvironmentId environmentId )
    {
        try
        {
            localPeer.removePeerEnvironmentKeyPair( environmentId );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void addInitiatorPeerEnvironmentPubKey( final String keyId, final String pek )
    {
        try
        {
            PGPPublicKeyRing pubKeyRing = PGPKeyUtil.readPublicKeyRing( pek );
            localPeer.addPeerEnvironmentPubKey( keyId, pubKeyRing );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Vni reserveVni( final Vni vni )
    {
        try
        {
            return localPeer.reserveVni( vni );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response setupTunnels( final String environmentId, final Map<String, String> peerIps )
    {
        Preconditions.checkNotNull( environmentId );
        Preconditions.checkNotNull( peerIps );
        try
        {
            int vlan = localPeer.setupTunnels( peerIps, environmentId );

            return Response.ok( vlan ).build();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public HostInterfaces getNetworkInterfaces()
    {
        try
        {
            return localPeer.getInterfaces();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public ResourceHostMetrics getResources()
    {
        try
        {
            return localPeer.getResourceHostMetrics();
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public HostId getResourceHostIdByContainerId( final ContainerId containerId )
    {
        Preconditions.checkNotNull( containerId );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( containerId.getId() ) );
        try
        {

            return localPeer.getResourceHostIdByContainerId( containerId );
        }
        catch ( Exception e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void resetP2PSecretKey( final P2PCredentials p2PCredentials )
    {
        try
        {
            localPeer.resetP2PSecretKey( p2PCredentials );
        }
        catch ( Exception e )
        {
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response setupP2PConnection( final P2PConfig config )
    {
        try
        {
            String mhP2pIP = localPeer.setupP2PConnection( config );
            return Response.ok( mhP2pIP ).build();
        }
        catch ( Exception e )
        {
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response setupInitialP2PConnection( final P2PConfig config )
    {
        try
        {
            localPeer.setupInitialP2PConnection( config );
            return Response.ok().build();
        }
        catch ( Exception e )
        {
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void removeP2PConnection( final String p2pHash )
    {
        try
        {
            localPeer.removeP2PConnection( p2pHash );
        }
        catch ( Exception e )
        {
            throw new WebApplicationException( e );
        }
    }


    @Override
    public void cleanupEnvironment( final EnvironmentId environmentId )
    {
        try
        {
            localPeer.cleanupEnvironment( environmentId );
        }
        catch ( Exception e )
        {
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response putAlert( final AlertEvent alertEvent )
    {
        try
        {
            localPeer.alert( alertEvent );

            return Response.accepted().build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getHistoricalMetrics( final String hostName, final DateTimeParam startTime,
                                          final DateTimeParam endTime )
    {
        try
        {
            return Response.ok( localPeer.getHistoricalMetrics( hostName, startTime.getDate(), endTime.getDate() ) )
                           .build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getResourceLimits( final String peerId )
    {
        try
        {
            return Response.ok( localPeer.getResourceLimits( peerId ) ).build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getControlNetworkConfig( final String peerId )
    {
        try
        {
            return Response.ok( localPeer.getControlNetworkConfig( peerId ) ).build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response updateControlNetworkConfig( final ControlNetworkConfig config )
    {

        Preconditions.checkNotNull( config );
        Preconditions.checkNotNull( config.getAddress() );
        Preconditions.checkNotNull( config.getP2pHash() );
        Preconditions.checkNotNull( config.getPeerId() );
        Preconditions.checkNotNull( config.getSecretKey() );
        Preconditions.checkArgument( config.getSecretKeyTtlSec() > 0 );

        try
        {
            return Response.ok( localPeer.updateControlNetworkConfig( config ) ).build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }


    @Override
    public Response getP2PSwarmDistances( final String p2pHash, final Integer count )
    {

        Preconditions.checkNotNull( p2pHash );
        Preconditions.checkNotNull( count );

        try
        {
            return Response.ok( localPeer.getP2PSwarmDistances( p2pHash, count ) ).build();
        }
        catch ( PeerException e )
        {
            LOGGER.error( e.getMessage(), e );
            throw new WebApplicationException( e );
        }
    }
}
