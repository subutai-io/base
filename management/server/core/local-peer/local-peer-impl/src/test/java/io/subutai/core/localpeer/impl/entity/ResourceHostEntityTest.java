package io.subutai.core.localpeer.impl.entity;


import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Sets;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.CommandUtil;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.host.HostInterfaceModel;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.host.ResourceHostInfo;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.HostNotFoundException;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.ResourceHostException;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.core.hostregistry.api.HostRegistry;
import io.subutai.core.kurjun.api.TemplateManager;
import io.subutai.core.metric.api.Monitor;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class ResourceHostEntityTest
{
    private static final String PEER_ID = UUID.randomUUID().toString();
    private static final String HOST_ID = UUID.randomUUID().toString();
    private static final String CONTAINER_HOST_ID = UUID.randomUUID().toString();
    private static final String CONTAINER_HOST_NAME = "hostname";
    private static final String TEMPLATE_NAME = "master";
    private static final String ENV_ID = "123";
    private static final int VLAN = 100;
    private static final int TIMEOUT = 120;
    private static final String HOSTNAME = "hostname";
    private static final HostArchitecture ARCH = HostArchitecture.AMD64;
    private static final String INTERFACE_NAME = "eth0";
    private static final String IP = "127.0.0.1/24";
    private static final String MAC = "mac";
    private static final String CONTAINER_STATUS_STARTED =
            "NAME                               STATE    HWADDR             IP" + "            Interface\n" +
                    "---------------------------------\n"
                    + "qwer                               RUNNING  00:16:3e:83:2c:2e  192.168.22.5  eth0";
    private static final String CONTAINER_STATUS_STOPPED =
            "NAME                               STATE    HWADDR             IP" + "            Interface\n" +
                    "---------------------------------\n"
                    + "qwer                               STOPPED  00:16:3e:83:2c:2e  192.168.22.5  eth0";
    @Mock
    ContainerHostEntity containerHost;
    @Mock
    ExecutorService singleThreadExecutorService;
    @Mock
    Monitor monitor;
    @Mock
    CommandUtil commandUtil;
    @Mock
    TemplateManager registry;
    @Mock
    HostRegistry hostRegistry;
    @Mock
    Peer peer;
    @Mock
    ResourceHostInfo hostInfo;
    @Mock
    HostInterfaceModel anHostInterface;
    @Mock
    Callable callable;
    @Mock
    CommandResult commandResult;
    @Mock
    Future future;
    @Mock
    TemplateKurjun template;


    ResourceHostEntity resourceHostEntity;
    @Mock
    private HostInterfaces hostInterfaces;

    @Mock
    private ContainerQuota quota;


    @Before
    public void setUp() throws Exception
    {
        when( hostInterfaces.getAll() ).thenReturn( Sets.newHashSet( anHostInterface ) );
        when( hostInfo.getId() ).thenReturn( HOST_ID );
        when( hostInfo.getHostname() ).thenReturn( HOSTNAME );
        when( hostInfo.getArch() ).thenReturn( ARCH );
        when( hostInfo.getHostInterfaces() ).thenReturn( hostInterfaces );
        when( anHostInterface.getName() ).thenReturn( INTERFACE_NAME );
        when( anHostInterface.getIp() ).thenReturn( IP );

        resourceHostEntity = new ResourceHostEntity( PEER_ID, hostInfo );
        resourceHostEntity.setHostRegistry( hostRegistry );
        //        resourceHostEntity.setMonitor( monitor );
        resourceHostEntity.setRegistry( registry );
        resourceHostEntity.setPeer( peer );
        resourceHostEntity.singleThreadExecutorService = singleThreadExecutorService;
        resourceHostEntity.commandUtil = commandUtil;
        when( containerHost.getId() ).thenReturn( CONTAINER_HOST_ID );
        when( containerHost.getHostname() ).thenReturn( CONTAINER_HOST_NAME );
        when( commandUtil.execute( any( RequestBuilder.class ), eq( resourceHostEntity ) ) )
                .thenReturn( commandResult );
        when( singleThreadExecutorService.submit( any( Callable.class ) ) ).thenReturn( future );
    }


    @Test
    public void testDispose() throws Exception
    {
        resourceHostEntity.init();
        resourceHostEntity.dispose();

        verify( singleThreadExecutorService ).shutdown();
        verify( singleThreadExecutorService ).shutdown();
    }


    @Test
    public void testQueueSequentialTask() throws Exception
    {
        resourceHostEntity.queueSequentialTask( callable );

        verify( singleThreadExecutorService ).submit( callable );
    }


    @Test
    public void testAddContainerHost() throws Exception
    {
        resourceHostEntity.addContainerHost( containerHost );

        verify( containerHost ).setParent( resourceHostEntity );
    }


    @Test
    public void testGetContainerHostState() throws Exception
    {
        try
        {
            resourceHostEntity.getContainerHostState( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }


        when( commandResult.getStdOut() ).thenReturn( CONTAINER_STATUS_STARTED );
        resourceHostEntity.addContainerHost( containerHost );

        ContainerHostState state = resourceHostEntity.getContainerHostState( containerHost );

        assertEquals( ContainerHostState.RUNNING, state );


        when( commandResult.getStdOut() ).thenReturn( "" );

        state = resourceHostEntity.getContainerHostState( containerHost );

        assertEquals( ContainerHostState.UNKNOWN, state );

        doThrow( new CommandException( "" ) ).when( commandUtil )
                                             .execute( any( RequestBuilder.class ), eq( resourceHostEntity ) );

        try
        {
            resourceHostEntity.getContainerHostState( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }
    }


    @Test
    public void testGetContainerHosts() throws Exception
    {
        resourceHostEntity.addContainerHost( containerHost );

        Set<ContainerHost> containerHosts = resourceHostEntity.getContainerHosts();

        assertTrue( containerHosts.contains( containerHost ) );
    }


    @Test
    public void testStartContainerHost() throws Exception
    {

        try
        {
            resourceHostEntity.startContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }

        when( containerHost.isConnected() ).thenReturn( true );
        when( commandResult.getStdOut() ).thenReturn( CONTAINER_STATUS_STARTED );
        resourceHostEntity.addContainerHost( containerHost );

        resourceHostEntity.startContainerHost( containerHost );

        verify( commandUtil, atLeastOnce() ).execute( any( RequestBuilder.class ), eq( resourceHostEntity ) );

        when( commandResult.getStdOut() ).thenReturn( CONTAINER_STATUS_STOPPED );

        try
        {
            resourceHostEntity.startContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }

        when( commandResult.getStdOut() ).thenReturn( CONTAINER_STATUS_STARTED );

        doThrow( new CommandException( "" ) ).when( commandUtil )
                                             .execute( any( RequestBuilder.class ), eq( resourceHostEntity ) );

        try
        {
            resourceHostEntity.startContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }
    }


    @Test
    public void testStopContainerHost() throws Exception
    {

        try
        {
            resourceHostEntity.stopContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }

        resourceHostEntity.addContainerHost( containerHost );

        resourceHostEntity.stopContainerHost( containerHost );

        verify( commandUtil, atLeastOnce() ).execute( any( RequestBuilder.class ), eq( resourceHostEntity ) );

        doThrow( new CommandException( "" ) ).when( commandUtil )
                                             .execute( any( RequestBuilder.class ), eq( resourceHostEntity ) );

        try
        {
            resourceHostEntity.stopContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }
    }


    @Test( expected = ResourceHostException.class )
    public void testDestroyContainerHost() throws Exception
    {
        try
        {
            resourceHostEntity.destroyContainerHost( containerHost );
            fail( "Expected ResourceHostException" );
        }
        catch ( ResourceHostException e )
        {
        }

        resourceHostEntity.addContainerHost( containerHost );

        resourceHostEntity.destroyContainerHost( containerHost );

        verify( future ).get();

        doThrow( new ExecutionException( null ) ).when( future ).get();


        resourceHostEntity.destroyContainerHost( containerHost );
    }


    @Test
    public void testGetContainerHostByName() throws Exception
    {
        try
        {
            resourceHostEntity.getContainerHostByName( HOSTNAME );
            fail( "Expected HostNotFoundException" );
        }
        catch ( HostNotFoundException e )
        {
        }

        resourceHostEntity.addContainerHost( containerHost );

        ContainerHost containerHost1 = resourceHostEntity.getContainerHostByName( HOSTNAME );

        assertEquals( containerHost, containerHost1 );
    }


    @Test
    public void testGetContainerHostById() throws Exception
    {

        try
        {
            resourceHostEntity.getContainerHostById( CONTAINER_HOST_ID );
            fail( "Expected HostNotFoundException" );
        }
        catch ( HostNotFoundException e )
        {
        }

        resourceHostEntity.addContainerHost( containerHost );

        ContainerHost containerHost1 = resourceHostEntity.getContainerHostById( CONTAINER_HOST_ID );

        assertEquals( containerHost, containerHost1 );
    }


    @Test( expected = ResourceHostException.class )
    public void testCreateContainer() throws Exception
    {


        try
        {
            resourceHostEntity.createContainer( TEMPLATE_NAME, HOSTNAME, quota, IP, VLAN, TIMEOUT, ENV_ID );
            fail( "Expected HostNotFoundException" );
        }
        catch ( ResourceHostException e )
        {
        }


        when( registry.getTemplate( TEMPLATE_NAME ) ).thenReturn( template );
        resourceHostEntity.addContainerHost( containerHost );


        try
        {
            resourceHostEntity.createContainer( TEMPLATE_NAME, HOSTNAME, quota, IP, VLAN, TIMEOUT, ENV_ID );
            fail( "Expected HostNotFoundException" );
        }
        catch ( ResourceHostException e )
        {
        }

        resourceHostEntity.removeContainerHost( containerHost );


        resourceHostEntity.createContainer( TEMPLATE_NAME, HOSTNAME, quota, IP, VLAN, TIMEOUT, ENV_ID );

        verify( future ).get();

        doThrow( new ExecutionException( null ) ).when( future ).get();


        resourceHostEntity.createContainer( TEMPLATE_NAME, HOSTNAME, quota, IP, VLAN, TIMEOUT, ENV_ID );
    }
}

