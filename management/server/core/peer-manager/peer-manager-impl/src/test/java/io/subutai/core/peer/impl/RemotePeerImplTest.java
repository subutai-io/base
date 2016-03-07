package io.subutai.core.peer.impl;


import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.exception.HTTPException;
import io.subutai.common.network.Vni;
import io.subutai.common.peer.ContainerGateway;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.MessageRequest;
import io.subutai.common.peer.MessageResponse;
import io.subutai.common.peer.Payload;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerInfo;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.util.JsonUtil;
import io.subutai.common.util.RestUtil;
import io.subutai.core.messenger.api.Message;
import io.subutai.core.messenger.api.MessageException;
import io.subutai.core.messenger.api.Messenger;
import io.subutai.core.peer.impl.command.BlockingCommandCallback;
import io.subutai.core.peer.impl.command.CommandResponseListener;
import io.subutai.core.peer.impl.request.MessageResponseListener;
import io.subutai.core.security.api.SecurityManager;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
@Ignore
public class RemotePeerImplTest
{
    private static final String PATH = "path";
    private static final String ALIAS = "alias";
    private static final String PARAM_NAME = "param";
    private static final String PARAM_VALUE = "param value";
    private static final String HEADER_NAME = "header";
    private static final String HEADER_VALUE = "header value";
    private static final String TEMPLATE_NAME = "master";
    private static final String CONTAINER_ID = UUID.randomUUID().toString();
    private static final String ENV_ID = UUID.randomUUID().toString();
    private static final String PEER_ID = UUID.randomUUID().toString();
    private static final String IP = "127.0.0.1";
    private static final int PID = 123;
    private static final int VLAN = 123;
    private static final int QUOTA = 123;
    private static final int PERCENT = 75;
    private static final Set<Integer> CPU_SET = Sets.newHashSet( 1, 2, 3 );
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final int TIMEOUT = 100;
    private static final String RECIPIENT = "recipient";
    private static final Object REQUEST = new Object();
    private static final String SUBNET = "192.168.1.0/24";
    private static final String RESPONSE = "RESPONSE";
    private static final String P2P_IP = "10.11.0.1";
    @Mock
    LocalPeer localPeer;
    @Mock
    PeerInfo peerInfo;
    @Mock
    Messenger messenger;
    @Mock
    CommandResponseListener commandResponseListener;
    @Mock
    MessageResponseListener messageResponseListener;
    @Mock
    RestUtil restUtil;
    @Mock
    JsonUtil jsonUtil;
    @Mock
    HTTPException httpException;
    @Mock
    EnvironmentContainerHost containerHost;
    @Mock
    CommandCallback commandCallback;
    @Mock
    Semaphore semaphore;
    @Mock
    Message message;
    @Mock
    MessageException messageException;

    Map<String, String> params;
    Map<String, String> headers;

    RequestBuilder requestBuilder;

    RemotePeerImpl remotePeer;

    Map<String, String> peerMap = new HashMap<>();
    @Mock
    private WebClient webClient;

    @Mock
    ContainerId containerId;

    @Mock
    private EnvironmentId environmentId;
    @Mock
    private ContainerGateway containerGateway;

    @Mock
    private SecurityManager securityManager;
    @Mock
    private EnvironmentId envId;


    @Before
    public void setUp() throws Exception
    {
        peerMap = new HashMap<>();
        peerMap.put( IP, P2P_IP );
        requestBuilder = new RequestBuilder( "pwd" );
        params = Maps.newHashMap();
        params.put( PARAM_NAME, PARAM_VALUE );
        headers = Maps.newHashMap();
        headers.put( HEADER_NAME, HEADER_VALUE );
        remotePeer = spy( new RemotePeerImpl( localPeer.getId(), securityManager, peerInfo, messenger,
                commandResponseListener, messageResponseListener, null ) );
        remotePeer.restUtil = restUtil;
        remotePeer.jsonUtil = jsonUtil;
        when( containerHost.getId() ).thenReturn( CONTAINER_ID );
        when( containerHost.getContainerId() ).thenReturn( containerId );
        when( containerId.getId() ).thenReturn( CONTAINER_ID );
        when( environmentId.getId() ).thenReturn( ENV_ID );
        when( containerId.getEnvironmentId() ).thenReturn( environmentId );
        when( localPeer.getId() ).thenReturn( PEER_ID );
        when( peerInfo.getId() ).thenReturn( PEER_ID );
        when( remotePeer.getId() ).thenReturn( PEER_ID );
        //        when( remotePeer.getPeerInfo() ).thenReturn( new PeerInfo() );
        when( containerHost.isConnected() ).thenReturn( true );
        when( envId.getId() ).thenReturn( ENV_ID );
        when( containerHost.getEnvironmentId() ).thenReturn( envId );
        when( messenger.createMessage( anyObject() ) ).thenReturn( message );
        when( message.getId() ).thenReturn( MESSAGE_ID );
    }


    private void throwException() throws HTTPException
    {
        doThrow( httpException ).when( restUtil )
                                .request( any( RestUtil.RequestType.class ), anyString(), anyString(), anyMap(),
                                        anyMap(), any() );
    }


    private void throwWebClientException() throws HTTPException
    {
        doThrow( httpException ).when( webClient ).post( anyObject(), any( Class.class ) );
    }


    @Test
    public void testRequest() throws Exception
    {
        remotePeer.request( RestUtil.RequestType.GET, PATH, ALIAS, params, headers );

        verify( restUtil )
                .request( eq( RestUtil.RequestType.GET ), anyString(), eq( ALIAS ), eq( params ), eq( headers ),
                        any() );
    }


    @Test
    public void testGet() throws Exception
    {

        remotePeer.get( PATH, ALIAS, params, headers );

        verify( restUtil )
                .request( eq( RestUtil.RequestType.GET ), anyString(), eq( ALIAS ), eq( params ), eq( headers ),
                        any() );
    }


    @Test
    public void testPost() throws Exception
    {
        remotePeer.post( PATH, ALIAS, params, headers );

        verify( restUtil )
                .request( eq( RestUtil.RequestType.POST ), anyString(), eq( ALIAS ), eq( params ), eq( headers ),
                        any() );
    }


    @Test
    public void testDelete() throws Exception
    {
        remotePeer.delete( PATH, ALIAS, params, headers );

        verify( restUtil )
                .request( eq( RestUtil.RequestType.DELETE ), anyString(), eq( ALIAS ), eq( params ), eq( headers ),
                        any() );
    }


    @Test
    public void testGetId() throws Exception
    {
        remotePeer.getId();

        verify( peerInfo ).getId();
    }


    //    @Test( expected = PeerException.class )
    //    public void testGetRemoteId() throws Exception
    //    {
    //        String ID = UUID.randomUUID().toString();
    //        when( restUtil.request( eq( RestUtil.RequestType.GET ), anyString(), anyString(), anyMap(), anyMap() ) )
    //                .thenReturn( ID );
    //
    //        PeerInfo id = remotePeer.getPeerInfo();
    //
    //        assertEquals( ID, id.toString() );
    //
    //        throwException();
    //
    //        //        remotePeer.getRemoteId();
    //    }


    //    @Test( expected = PeerException.class )
    //    public void testIsOnline() throws Exception
    //    {
    //        UUID ID = UUID.randomUUID();
    //        when( restUtil.request( eq( RestUtil.RequestType.GET ), anyString(), anyString(), anyMap(), anyMap() ) )
    //                .thenReturn( ID.toString() );
    //        when( peerInfo.getId() ).thenReturn( ID.toString() );
    //
    //        assertTrue( remotePeer.isOnline() );
    //
    //        throwException();
    //
    //        remotePeer.isOnline();
    //    }


    @Test
    public void testIsLocal() throws Exception
    {
        assertFalse( remotePeer.isLocal() );
    }


    @Test
    public void testGetName() throws Exception
    {
        remotePeer.getName();

        verify( peerInfo ).getName();
    }


    @Test
    public void testGetOwnerId() throws Exception
    {
        remotePeer.getOwnerId();

        verify( peerInfo ).getOwnerId();
    }


    //    @Test
    //    public void testGetPeerInfo() throws Exception
    //    {
    //        assertEquals( peerInfo, remotePeer.getPeerInfo() );
    //    }


    @Test( expected = PeerException.class )
    public void testGetTemplate() throws Exception
    {
        remotePeer.getTemplate( TEMPLATE_NAME );

        verify( jsonUtil ).from( anyString(), eq( TemplateKurjun.class ) );

        throwException();

        remotePeer.getTemplate( TEMPLATE_NAME );
    }


    @Test( expected = PeerException.class )
    public void testStartContainer() throws Exception
    {
        remotePeer.startContainer( containerHost.getContainerId() );

        //verify( localPeer ).getId();

        throwWebClientException();

        remotePeer.startContainer( containerHost.getContainerId() );
    }


    @Test( expected = PeerException.class )
    public void testStopContainer() throws Exception
    {
        remotePeer.stopContainer( containerHost.getContainerId() );

        //verify( localPeer ).getId();

        throwWebClientException();

        remotePeer.stopContainer( containerHost.getContainerId() );
    }


    @Test( expected = PeerException.class )
    public void testDestroyContainer() throws Exception
    {
        remotePeer.destroyContainer( containerHost.getContainerId() );

        //verify( localPeer ).getId();

        throwWebClientException();

        remotePeer.destroyContainer( containerHost.getContainerId() );
    }


    @Test( expected = PeerException.class )
    public void testSetDefaultGateway() throws Exception
    {
        //remotePeer.setDefaultGateway( containerGateway );

        //verify( localPeer ).getId();

        throwException();

        //remotePeer.setDefaultGateway( containerGateway );
    }


    //    @Test
    //    public void testIsConnected() throws Exception
    //    {
    //        assertTrue( remotePeer.isConnected( containerHost.getContainerId() ) );
    //
    //        throwException();
    //
    //        //assertFalse( remotePeer.isConnected( containerHost ) );
    //    }


    @Test( expected = PeerException.class )
    public void testGetProcessResourceUsage() throws Exception
    {
        remotePeer.getProcessResourceUsage( containerHost.getContainerId(), PID );

        //verify( localPeer ).getId();

        throwException();

        remotePeer.getProcessResourceUsage( containerHost.getContainerId(), PID );
    }


    @Test( expected = PeerException.class )
    public void testGetContainerHostState() throws Exception
    {
        remotePeer.getContainerState( containerHost.getContainerId() );

        //verify( localPeer ).getId();

        throwWebClientException();

        remotePeer.getContainerState( containerHost.getContainerId() );
    }


    @Test( expected = PeerException.class )
    public void testGetCpuSet() throws Exception
    {
        when( jsonUtil.from( anyString(), any( Type.class ) ) ).thenReturn( CPU_SET );

        remotePeer.getCpuSet( containerHost );

        //verify( localPeer ).getId();

        throwException();

        remotePeer.getCpuSet( containerHost );
    }


    @Test( expected = PeerException.class )
    public void testSetCpuSet() throws Exception
    {
        remotePeer.setCpuSet( containerHost, CPU_SET );

        //verify( localPeer ).getId();

        throwException();

        remotePeer.setCpuSet( containerHost, CPU_SET );
    }


    @Test
    public void testExecute() throws Exception
    {
        BlockingCommandCallback blockingCommandCallback = mock( BlockingCommandCallback.class );
        when( remotePeer.getBlockingCommandCallback( any( CommandCallback.class ) ) )
                .thenReturn( blockingCommandCallback );

        remotePeer.execute( requestBuilder, containerHost );

        verify( blockingCommandCallback ).getCommandResult();

        remotePeer.execute( requestBuilder, containerHost, commandCallback );

        verify( blockingCommandCallback, times( 2 ) ).getCommandResult();
    }


    @Test
    public void testExecuteAsync() throws Exception
    {
        remotePeer.executeAsync( requestBuilder, containerHost );

        verify( commandResponseListener )
                .addCallback( any( UUID.class ), any( CommandCallback.class ), anyInt(), any( Semaphore.class ) );

        remotePeer.executeAsync( requestBuilder, containerHost, commandCallback );

        verify( commandResponseListener )
                .addCallback( any( UUID.class ), eq( commandCallback ), anyInt(), any( Semaphore.class ) );

        remotePeer.executeAsync( requestBuilder, containerHost, commandCallback, semaphore );

        verify( commandResponseListener )
                .addCallback( any( UUID.class ), eq( commandCallback ), anyInt(), eq( semaphore ) );

        doThrow( messageException ).when( messenger )
                                   .sendMessage( any( Peer.class ), any( Message.class ), anyString(), anyInt(),
                                           anyMap() );

        try
        {
            remotePeer.executeAsync( requestBuilder, containerHost, commandCallback, semaphore );
            fail( "Expected CommandException" );
        }
        catch ( CommandException e )
        {
        }

        try
        {
            Host host = mock( Host.class );
            remotePeer.executeAsync( requestBuilder, host, commandCallback, semaphore );
            fail( "Expected CommandException" );
        }
        catch ( CommandException e )
        {
        }
        try
        {
            when( containerHost.isConnected() ).thenReturn( false );
            remotePeer.executeAsync( requestBuilder, containerHost, commandCallback, semaphore );
            fail( "Expected CommandException" );
        }
        catch ( CommandException e )
        {
        }
    }


    @Test( expected = PeerException.class )
    public void testSendRequestInternal() throws Exception
    {
        remotePeer.sendRequestInternal( REQUEST, RECIPIENT, TIMEOUT, headers );

        verify( messenger ).sendMessage( any( Peer.class ), eq( message ), anyString(), eq( TIMEOUT ), eq( headers ) );


        doThrow( messageException ).when( messenger )
                                   .sendMessage( any( Peer.class ), any( Message.class ), anyString(), anyInt(),
                                           anyMap() );

        remotePeer.sendRequestInternal( REQUEST, RECIPIENT, TIMEOUT, headers );
    }


    @Test
    public void testSendRequest() throws Exception
    {

        remotePeer.sendRequest( REQUEST, RECIPIENT, TIMEOUT, headers );

        verify( messenger ).sendMessage( any( Peer.class ), eq( message ), anyString(), eq( TIMEOUT ), eq( headers ) );

        MessageResponse messageResponse = mock( MessageResponse.class );
        when( messageResponseListener.waitResponse( any( MessageRequest.class ), anyInt(), anyInt() ) )
                .thenReturn( messageResponse );

        remotePeer.sendRequest( REQUEST, RECIPIENT, TIMEOUT, Object.class, TIMEOUT, headers );

        verify( messageResponse ).getException();

        Payload payload = mock( Payload.class );
        when( messageResponse.getPayload() ).thenReturn( payload );


        remotePeer.sendRequest( REQUEST, RECIPIENT, TIMEOUT, Object.class, TIMEOUT, headers );

        verify( payload ).getMessage( any( Class.class ) );

        when( messageResponse.getException() ).thenReturn( "exception" );

        try
        {
            remotePeer.sendRequest( REQUEST, RECIPIENT, TIMEOUT, Object.class, TIMEOUT, headers );
            fail( "Expected PeerException" );
        }
        catch ( PeerException e )
        {
        }
    }


    @Test( expected = PeerException.class )
    public void testReserveVni() throws Exception
    {
        Vni vni = mock( Vni.class );
        when( vni.getEnvironmentId() ).thenReturn( ENV_ID );


        assertEquals( VLAN, remotePeer.reserveVni( vni ) );

        throwException();

        remotePeer.reserveVni( vni );
    }


    @Test( expected = PeerException.class )
    public void testGetGateways() throws Exception
    {
        remotePeer.getGateways();

        verify( restUtil )
                .request( eq( RestUtil.RequestType.GET ), anyString(), anyString(), anyMap(), anyMap(), any() );

        throwException();

        remotePeer.getGateways();
    }


    @Test( expected = PeerException.class )
    public void testGetReservedVnis() throws Exception
    {
        remotePeer.getReservedVnis();

        verify( restUtil )
                .request( eq( RestUtil.RequestType.GET ), anyString(), anyString(), anyMap(), anyMap(), any() );

        throwException();

        remotePeer.getReservedVnis();
    }
}
