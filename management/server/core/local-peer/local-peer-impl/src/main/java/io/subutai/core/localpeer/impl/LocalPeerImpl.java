package io.subutai.core.localpeer.impl;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.security.PermitAll;
import javax.naming.NamingException;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.net.util.SubnetUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.CommandUtil;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.dao.DaoManager;
import io.subutai.common.environment.Containers;
import io.subutai.common.environment.CreateEnvironmentContainerGroupRequest;
import io.subutai.common.environment.CreateEnvironmentContainerResponseCollector;
import io.subutai.common.environment.HostAddresses;
import io.subutai.common.environment.PrepareTemplatesRequest;
import io.subutai.common.environment.PrepareTemplatesResponseCollector;
import io.subutai.common.environment.RhP2pIp;
import io.subutai.common.environment.SshPublicKeys;
import io.subutai.common.exception.DaoException;
import io.subutai.common.host.ContainerHostInfo;
import io.subutai.common.host.ContainerHostInfoModel;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.host.HostId;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.HostInterface;
import io.subutai.common.host.HostInterfaceModel;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.host.ResourceHostInfo;
import io.subutai.common.mdc.SubutaiExecutors;
import io.subutai.common.metric.ProcessResourceUsage;
import io.subutai.common.metric.QuotaAlertValue;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.network.DomainLoadBalanceStrategy;
import io.subutai.common.network.NetworkResource;
import io.subutai.common.network.NetworkResourceImpl;
import io.subutai.common.network.ReservedNetworkResources;
import io.subutai.common.network.UsedNetworkResources;
import io.subutai.common.peer.AlertEvent;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.HostNotFoundException;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.Payload;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerInfo;
import io.subutai.common.peer.RequestListener;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.peer.ResourceHostException;
import io.subutai.common.protocol.Disposable;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2PConnection;
import io.subutai.common.protocol.P2PConnections;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.protocol.P2pIps;
import io.subutai.common.protocol.PingDistance;
import io.subutai.common.protocol.PingDistances;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.protocol.Tunnel;
import io.subutai.common.protocol.Tunnels;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.quota.QuotaException;
import io.subutai.common.resource.HistoricalMetrics;
import io.subutai.common.resource.PeerResources;
import io.subutai.common.security.PublicKeyContainer;
import io.subutai.common.security.crypto.pgp.KeyPair;
import io.subutai.common.security.crypto.pgp.PGPKeyUtil;
import io.subutai.common.security.objects.KeyTrustLevel;
import io.subutai.common.security.objects.SecurityKeyType;
import io.subutai.common.settings.Common;
import io.subutai.common.settings.SystemSettings;
import io.subutai.common.task.CloneRequest;
import io.subutai.common.task.CloneResponse;
import io.subutai.common.task.ImportTemplateRequest;
import io.subutai.common.task.QuotaRequest;
import io.subutai.common.task.Task;
import io.subutai.common.task.TaskCallbackHandler;
import io.subutai.common.util.CollectionUtil;
import io.subutai.common.util.ControlNetworkUtil;
import io.subutai.common.util.ExceptionUtil;
import io.subutai.common.util.P2PUtil;
import io.subutai.common.util.ServiceLocator;
import io.subutai.core.executor.api.CommandExecutor;
import io.subutai.core.hostregistry.api.HostDisconnectedException;
import io.subutai.core.hostregistry.api.HostListener;
import io.subutai.core.hostregistry.api.HostRegistry;
import io.subutai.core.kurjun.api.TemplateManager;
import io.subutai.core.localpeer.impl.command.CommandRequestListener;
import io.subutai.core.localpeer.impl.container.CloneTask;
import io.subutai.core.localpeer.impl.container.CreateEnvironmentContainerGroupRequestListener;
import io.subutai.core.localpeer.impl.container.ImportTask;
import io.subutai.core.localpeer.impl.container.PrepareTemplateRequestListener;
import io.subutai.core.localpeer.impl.container.QuotaTask;
import io.subutai.core.localpeer.impl.dao.NetworkResourceDaoImpl;
import io.subutai.core.localpeer.impl.dao.ResourceHostDataService;
import io.subutai.core.localpeer.impl.entity.AbstractSubutaiHost;
import io.subutai.core.localpeer.impl.entity.ContainerHostEntity;
import io.subutai.core.localpeer.impl.entity.NetworkResourceEntity;
import io.subutai.core.localpeer.impl.entity.ResourceHostEntity;
import io.subutai.core.lxc.quota.api.QuotaManager;
import io.subutai.core.metric.api.Monitor;
import io.subutai.core.metric.api.MonitorException;
import io.subutai.core.network.api.NetworkManager;
import io.subutai.core.network.api.NetworkManagerException;
import io.subutai.core.registration.api.RegistrationManager;
import io.subutai.core.security.api.SecurityManager;
import io.subutai.core.security.api.crypto.EncryptionTool;
import io.subutai.core.security.api.crypto.KeyManager;


/**
 * Local peer implementation
 */
@PermitAll
public class LocalPeerImpl implements LocalPeer, HostListener, Disposable
{
    private static final Logger LOG = LoggerFactory.getLogger( LocalPeerImpl.class );

    private static final String GATEWAY_INTERFACE_NAME_REGEX = "^gw-(\\d+)$";
    private static final Pattern GATEWAY_INTERFACE_NAME_PATTERN = Pattern.compile( GATEWAY_INTERFACE_NAME_REGEX );
    private static final String P2P_INTERFACE_NAME_REGEX = "^p2p-(\\d+)$";
    private static final Pattern P2P_INTERFACE_NAME_PATTERN = Pattern.compile( P2P_INTERFACE_NAME_REGEX );

    private DaoManager daoManager;
    private TemplateManager templateRegistry;
    protected ResourceHost managementHost;
    protected Set<ResourceHost> resourceHosts = Sets.newHashSet();
    private CommandExecutor commandExecutor;
    private QuotaManager quotaManager;
    private Monitor monitor;
    protected ResourceHostDataService resourceHostDataService;
    private HostRegistry hostRegistry;
    protected CommandUtil commandUtil = new CommandUtil();
    protected ExceptionUtil exceptionUtil = new ExceptionUtil();
    protected Set<RequestListener> requestListeners = Sets.newHashSet();
    protected PeerInfo peerInfo;
    private SecurityManager securityManager;
    protected ServiceLocator serviceLocator = new ServiceLocator();


    protected boolean initialized = false;
    protected ExecutorService singleThreadExecutorService = SubutaiExecutors.newSingleThreadExecutor();
    private TaskManagerImpl taskManager;
    private NetworkResourceDaoImpl networkResourceDao;


    public LocalPeerImpl( DaoManager daoManager, TemplateManager templateRegistry, QuotaManager quotaManager,
                          CommandExecutor commandExecutor, HostRegistry hostRegistry, Monitor monitor,
                          SecurityManager securityManager )
    {
        this.daoManager = daoManager;
        this.templateRegistry = templateRegistry;
        this.quotaManager = quotaManager;
        this.monitor = monitor;
        this.commandExecutor = commandExecutor;
        this.hostRegistry = hostRegistry;
        this.securityManager = securityManager;
    }


    public void init() throws PeerException
    {
        LOG.debug( "********************************************** Initializing peer "
                + "******************************************" );
        try
        {
            initPeerInfo();

            //add command request listener
            addRequestListener( new CommandRequestListener() );
            //add command response listener

            //add create container requests listener
            addRequestListener( new CreateEnvironmentContainerGroupRequestListener( this ) );

            //add prepare templates listener
            addRequestListener( new PrepareTemplateRequestListener( this ) );


            resourceHostDataService = createResourceHostDataService();
            resourceHosts.clear();
            synchronized ( resourceHosts )
            {
                for ( ResourceHost resourceHost : resourceHostDataService.getAll() )
                {
                    resourceHosts.add( resourceHost );
                }
            }

            setResourceHostTransientFields( getResourceHosts() );

            taskManager = new TaskManagerImpl( this );

            this.networkResourceDao = new NetworkResourceDaoImpl( daoManager.getEntityManagerFactory() );
        }
        catch ( Exception e )
        {
            throw new LocalPeerInitializationError( "Failed to init Local Peer", e );
        }

        initialized = true;
    }


    protected void initPeerInfo()
    {

        peerInfo = new PeerInfo();
        peerInfo.setId( securityManager.getKeyManager().getPeerId() );
        peerInfo.setOwnerId( securityManager.getKeyManager().getPeerOwnerId() );
        peerInfo.setPublicUrl( SystemSettings.getPublicUrl() );
        peerInfo.setPublicSecurePort( SystemSettings.getPublicSecurePort() );
        peerInfo.setName( String.format( "Peer %s on %s", peerInfo.getId(), SystemSettings.getPublicUrl() ) );
    }


    @Override
    public void setPeerInfo( final PeerInfo peerInfo )
    {
        this.peerInfo.setId( peerInfo.getId() );
        this.peerInfo.setName( peerInfo.getName() );
        this.peerInfo.setOwnerId( peerInfo.getOwnerId() );
        this.peerInfo.setPublicUrl( peerInfo.getPublicUrl() );
        this.peerInfo.setPublicSecurePort( peerInfo.getPublicSecurePort() );
    }


    protected ResourceHostDataService createResourceHostDataService()
    {
        return new ResourceHostDataService( daoManager.getEntityManagerFactory() );
    }


    @Override
    public void dispose()
    {
        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            ( ( Disposable ) resourceHost ).dispose();
        }
    }


    private void setResourceHostTransientFields( Set<ResourceHost> resourceHosts )
    {
        for ( ResourceHost resourceHost : resourceHosts )
        {
            ( ( AbstractSubutaiHost ) resourceHost ).setPeer( this );
            final ResourceHostEntity resourceHostEntity = ( ResourceHostEntity ) resourceHost;
            resourceHostEntity.setRegistry( templateRegistry );
            resourceHostEntity.setHostRegistry( hostRegistry );
        }
    }


    @Override
    public String getId()
    {
        return peerInfo.getId();
    }


    @Override
    public String getName()
    {
        return peerInfo.getName();
    }


    @Override
    public String getOwnerId()
    {
        return peerInfo.getOwnerId();
    }


    @Override
    public PeerInfo getPeerInfo() throws PeerException
    {
        if ( peerInfo == null )
        {
            throw new PeerException( "Peer info unavailable." );
        }

        return peerInfo;
    }


    @Override
    public ContainerHostState getContainerState( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId );

        try
        {
            ContainerHostInfo containerHostInfo =
                    ( ContainerHostInfo ) hostRegistry.getHostInfoById( containerId.getId() );
            return containerHostInfo.getState();
        }
        catch ( Exception e )
        {
            throw new PeerException( "Error getting container state ", e );
        }
    }


    @Override
    public Containers getEnvironmentContainers( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId );

        Containers result = new Containers();
        try
        {
            Set<ContainerHost> containers = findContainersByEnvironmentId( environmentId.getId() );

            for ( ContainerHost c : containers )
            {
                ContainerHostInfo info;
                try
                {
                    info = hostRegistry.getContainerHostInfoById( c.getId() );
                }
                catch ( HostDisconnectedException e )
                {
                    info = new ContainerHostInfoModel( c );
                }
                result.addContainer( info );
            }
            return result;
        }
        catch ( Exception e )
        {
            throw new PeerException( "Error getting container state ", e );
        }
    }


    @Override
    public void configureHostsInEnvironment( final EnvironmentId environmentId, final HostAddresses hostAddresses )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkNotNull( hostAddresses, "Invalid HostAdresses" );
        Preconditions.checkArgument( !hostAddresses.isEmpty(), "No host addresses" );

        Set<Host> hosts = Sets.newHashSet();

        hosts.addAll( findContainersByEnvironmentId( environmentId.getId() ) );

        if ( hosts.isEmpty() )
        {
            return;
        }

        Map<Host, CommandResult> results = commandUtil
                .executeParallelSilent( getAddIpHostToEtcHostsCommand( hostAddresses.getHostAddresses() ), hosts );


        Set<Host> succeededHosts = Sets.newHashSet();
        Set<Host> failedHosts = Sets.newHashSet( hosts );

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            CommandResult result = resultEntry.getValue();
            Host host = resultEntry.getKey();

            if ( result.hasSucceeded() )
            {
                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        for ( Host failedHost : failedHosts )
        {
            LOG.error( "Host registration failed on host {}", failedHost.getHostname() );
        }

        if ( !failedHosts.isEmpty() )
        {
            throw new PeerException( "Failed to register all hosts" );
        }
    }


    protected RequestBuilder getAddIpHostToEtcHostsCommand( Map<String, String> hostAddresses )
    {
        StringBuilder cleanHosts = new StringBuilder( "localhost|127.0.0.1|" );
        StringBuilder appendHosts = new StringBuilder();

        for ( Map.Entry<String, String> hostEntry : hostAddresses.entrySet() )
        {
            String hostname = hostEntry.getKey();
            String ip = hostEntry.getValue();
            cleanHosts.append( ip ).append( "|" ).append( hostname ).append( "|" );
            appendHosts.append( "/bin/echo '" ).
                    append( ip ).append( " " ).
                               append( hostname ).append( "." ).append( Common.DEFAULT_DOMAIN_NAME ).
                               append( " " ).append( hostname ).
                               append( "' >> '/etc/hosts'; " );
        }

        if ( cleanHosts.length() > 0 )
        {
            //drop pipe | symbol
            cleanHosts.setLength( cleanHosts.length() - 1 );
            cleanHosts.insert( 0, "egrep -v '" );
            cleanHosts.append( "' /etc/hosts > etc-hosts-cleaned; mv etc-hosts-cleaned /etc/hosts;" );
            appendHosts.insert( 0, cleanHosts );
        }

        appendHosts.append( "/bin/echo '127.0.0.1 localhost " ).append( "' >> '/etc/hosts';" );

        return new RequestBuilder( appendHosts.toString() );
    }


    @Override
    public SshPublicKeys generateSshKeyForEnvironment( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );

        SshPublicKeys sshPublicKeys = new SshPublicKeys();

        Set<Host> hosts = Sets.newHashSet();

        hosts.addAll( findContainersByEnvironmentId( environmentId.getId() ) );

        if ( hosts.isEmpty() )
        {
            return sshPublicKeys;
        }

        //read ssh keys if exist
        Map<Host, CommandResult> results = commandUtil.executeParallelSilent( getReadSSHCommand(), hosts );

        Set<Host> succeededHosts = Sets.newHashSet();
        Set<Host> failedHosts = Sets.newHashSet( hosts );

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            CommandResult result = resultEntry.getValue();
            Host host = resultEntry.getKey();

            if ( result.hasSucceeded() && !Strings.isNullOrEmpty( result.getStdOut() ) )
            {
                sshPublicKeys.addSshPublicKey( result.getStdOut() );

                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        //create ssh keys
        results = commandUtil.executeParallelSilent( getCreateNReadSSHCommand(), failedHosts );

        succeededHosts = Sets.newHashSet();

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            CommandResult result = resultEntry.getValue();
            Host host = resultEntry.getKey();

            if ( result.hasSucceeded() && !Strings.isNullOrEmpty( result.getStdOut() ) )
            {
                sshPublicKeys.addSshPublicKey( result.getStdOut() );

                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        for ( Host failedHost : failedHosts )
        {
            LOG.error( "Failed to generate ssh key on host {}", failedHost.getHostname() );
        }

        if ( !failedHosts.isEmpty() )
        {
            throw new PeerException( "Failed to generate ssh keys on all hosts" );
        }


        return sshPublicKeys;
    }


    protected RequestBuilder getReadSSHCommand()
    {
        return new RequestBuilder( String.format( "cat %1$s/id_dsa.pub", Common.CONTAINER_SSH_FOLDER ) );
    }


    protected RequestBuilder getCreateNReadSSHCommand()
    {
        return new RequestBuilder( String.format( "rm -rf %1$s && " +
                        "mkdir -p %1$s && " +
                        "chmod 700 %1$s && " +
                        "ssh-keygen -t dsa -P '' -f %1$s/id_dsa -q && " + "cat %1$s/id_dsa.pub",
                Common.CONTAINER_SSH_FOLDER ) );
    }


    @Override
    public void configureSshInEnvironment( final EnvironmentId environmentId, final SshPublicKeys sshPublicKeys )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkNotNull( sshPublicKeys, "SshPublicKey is null" );
        Preconditions.checkArgument( !sshPublicKeys.isEmpty(), "No ssh keys" );

        Set<Host> hosts = Sets.newHashSet();

        hosts.addAll( findContainersByEnvironmentId( environmentId.getId() ) );

        if ( hosts.isEmpty() )
        {
            return;
        }

        //add keys in portions, since all can not fit into one command, it fails
        int i = 0;
        StringBuilder keysString = new StringBuilder();
        Set<String> keys = sshPublicKeys.getSshPublicKeys();

        for ( String key : keys )
        {
            keysString.append( key );
            i++;
            //send next 100 keys
            if ( i % 100 == 0 || i == keys.size() )
            {
                Set<Host> succeededHosts = Sets.newHashSet();
                Set<Host> failedHosts = Sets.newHashSet( hosts );

                Map<Host, CommandResult> results =
                        commandUtil.executeParallelSilent( getAppendSshKeysCommand( keysString.toString() ), hosts );

                keysString.setLength( 0 );

                for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
                {
                    CommandResult result = resultEntry.getValue();
                    Host host = resultEntry.getKey();

                    if ( result.hasSucceeded() )
                    {
                        succeededHosts.add( host );
                    }
                }

                failedHosts.removeAll( succeededHosts );

                for ( Host failedHost : failedHosts )
                {
                    LOG.error( "Failed to add ssh keys on host {}", failedHost.getHostname() );
                }

                if ( !failedHosts.isEmpty() )
                {
                    throw new PeerException( "Failed to add ssh keys on all hosts" );
                }
            }
        }

        //config ssh
        Set<Host> succeededHosts = Sets.newHashSet();
        Set<Host> failedHosts = Sets.newHashSet( hosts );

        Map<Host, CommandResult> results = commandUtil.executeParallelSilent( getConfigSSHCommand(), hosts );

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            CommandResult result = resultEntry.getValue();
            Host host = resultEntry.getKey();

            if ( result.hasSucceeded() )
            {
                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        for ( Host failedHost : failedHosts )
        {
            LOG.error( "Failed to configure ssh on host {}", failedHost.getHostname() );
        }

        if ( !failedHosts.isEmpty() )
        {
            throw new PeerException( "Failed to configure ssh on all hosts" );
        }
    }


    protected RequestBuilder getAppendSshKeysCommand( String keys )
    {
        return new RequestBuilder( String.format( "mkdir -p %1$s && " +
                "chmod 700 %1$s && " +
                "echo '%3$s' >> %2$s && " +
                "chmod 644 %2$s", Common.CONTAINER_SSH_FOLDER, Common.CONTAINER_SSH_FILE, keys ) );
    }


    protected RequestBuilder getConfigSSHCommand()
    {
        return new RequestBuilder( String.format( "echo 'Host *' > %1$s/config && " +
                "echo '    StrictHostKeyChecking no' >> %1$s/config && " +
                "chmod 644 %1$s/config", Common.CONTAINER_SSH_FOLDER ) );
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public PrepareTemplatesResponseCollector prepareTemplates( final PrepareTemplatesRequest request )
            throws PeerException
    {
        final PrepareTemplatesResponseCollector prepareTemplatesResponse =
                new PrepareTemplatesResponseCollector( getId() );
        for ( String resourceHostId : request.getTemplates().keySet() )
        {
            for ( String templateName : request.getTemplates().get( resourceHostId ) )
            {
                //todo move import template logic to RH
                ImportTask task = new ImportTask( new ImportTemplateRequest( resourceHostId, templateName ) );
                prepareTemplatesResponse.addTask( taskManager.schedule( task, prepareTemplatesResponse ) );
            }
        }

        prepareTemplatesResponse.waitResponsesWhileSucceeded();
        return prepareTemplatesResponse;
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public CreateEnvironmentContainerResponseCollector createEnvironmentContainerGroup(
            final CreateEnvironmentContainerGroupRequest requestGroup ) throws PeerException
    {
        Preconditions.checkNotNull( requestGroup );

        final CreateEnvironmentContainerResponseCollector response =
                new CreateEnvironmentContainerResponseCollector( getId() );
        final TaskCallbackHandler<CloneRequest, CloneResponse> successResultHandler = getCloneSuccessHandler( this );

        NetworkResource reservedNetworkResource =
                getReservedNetworkResources().findByEnvironmentId( requestGroup.getEnvironmentId() );

        if ( reservedNetworkResource == null )
        {
            throw new PeerException(
                    String.format( "No reserved vni found for environment %s", requestGroup.getEnvironmentId() ) );
        }

        for ( final CloneRequest request : requestGroup.getRequests() )
        {
            try
            {

                int rhCoresNumber = getResourceHostById( request.getResourceHostId() ).getNumberOfCpuCores();

                //todo move container clone logic to RH
                CloneTask task = new CloneTask( request, reservedNetworkResource.getVlan(), rhCoresNumber );

                task.onSuccess( successResultHandler );

                response.addTask( taskManager.schedule( task, response ) );
            }
            catch ( Exception e )
            {
                LOG.error( e.getMessage(), e );
            }
        }

        response.waitAllResponses();
        return response;
    }


    private TaskCallbackHandler<CloneRequest, CloneResponse> getCloneSuccessHandler( final LocalPeer localPeer )
    {
        return new TaskCallbackHandler<CloneRequest, CloneResponse>()
        {
            @Override
            public void handle( Task task, CloneRequest request, CloneResponse response ) throws Exception
            {

                Preconditions.checkNotNull( response, "Task response could not be null" );


                try
                {
                    QuotaTask quotaTask = new QuotaTask( quotaManager,
                            new QuotaRequest( request.getResourceHostId(), request.getHostname(),
                                    request.getContainerSize() ) );

                    taskManager.schedule( quotaTask, null );

                    final HostInterfaces interfaces = new HostInterfaces();
                    interfaces.addHostInterface(
                            new HostInterfaceModel( Common.DEFAULT_CONTAINER_INTERFACE, response.getIp() ) );
                    final String hostId = response.getContainerId();
                    final String localPeerId = localPeer.getId();
                    final HostArchitecture arch = request.getTemplateArch();
                    final String hostname = request.getHostname();
                    ContainerHostEntity containerHostEntity =
                            new ContainerHostEntity( localPeerId, hostId, hostname, arch, interfaces,
                                    request.getContainerName(), request.getTemplateName(), arch.name(),
                                    request.getEnvironmentId(), request.getOwnerId(), request.getInitiatorPeerId(),
                                    request.getContainerSize(), ContainerHostState.RUNNING );

                    registerContainer( request.getResourceHostId(), containerHostEntity );

                    //wait for container
                    boolean isRunning = false;
                    long waitStart = System.currentTimeMillis();
                    while ( !isRunning
                            && System.currentTimeMillis() - waitStart < Common.WAIT_CONTAINER_CONNECTION_SEC * 1000 )
                    {
                        try
                        {
                            isRunning = hostRegistry.getContainerHostInfoById( hostId ).getState()
                                    == ContainerHostState.RUNNING;
                        }
                        catch ( HostDisconnectedException e )
                        {
                            //ignore
                        }
                        if ( !isRunning )
                        {
                            Thread.sleep( 100 );
                        }
                    }
                }
                catch ( Exception e )
                {
                    LOG.error( "Error on registering container.", e );
                    throw new PeerException( "Error on registering container.", e );
                }
            }
        };
    }


    protected void registerContainer( String resourceHostId, ContainerHostEntity containerHostEntity ) throws Exception
    {
        ResourceHost resourceHost = getResourceHostById( resourceHostId );

        resourceHost.addContainerHost( containerHostEntity );

        signContainerKeyWithPEK( containerHostEntity.getId(), containerHostEntity.getEnvironmentId() );

        resourceHostDataService.saveOrUpdate( resourceHost );

        LOG.debug( "New container host registered: " + containerHostEntity.getHostname() );
    }


    private void signContainerKeyWithPEK( String containerId, EnvironmentId envId ) throws PeerException
    {
        String pairId = String.format( "%s-%s", getId(), envId.getId() );
        final PGPSecretKeyRing pekSecKeyRing = securityManager.getKeyManager().getSecretKeyRing( pairId );
        try
        {
            PGPPublicKeyRing containerPub = securityManager.getKeyManager().getPublicKeyRing( containerId );

            PGPPublicKeyRing signedKey = securityManager.getKeyManager().setKeyTrust( pekSecKeyRing, containerPub,
                    KeyTrustLevel.Full.getId() );

            securityManager.getKeyManager().updatePublicKeyRing( signedKey );
        }
        catch ( Exception ex )
        {
            throw new PeerException( ex );
        }
    }


    @PermitAll
    @Override
    public Set<ContainerHost> findContainersByEnvironmentId( final String environmentId )
    {
        Preconditions.checkNotNull( environmentId, "Invalid environment id" );

        Set<ContainerHost> result = new HashSet<>();

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            result.addAll( resourceHost.getContainerHostsByEnvironmentId( environmentId ) );
        }
        return result;
    }


    @Override
    public ContainerHost findContainerById( final ContainerId containerId )
    {
        Preconditions.checkNotNull( containerId, "Invalid container id" );
        Preconditions.checkNotNull( containerId.getId(), "Invalid container id" );

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            try
            {
                return resourceHost.getContainerHostById( containerId.getId() );
            }
            catch ( HostNotFoundException ignore )
            {
                // ignore
            }
        }
        return null;
    }


    @Override
    public Set<ContainerHost> findContainersByOwnerId( final String ownerId )
    {
        Preconditions.checkNotNull( ownerId, "Specify valid owner" );


        Set<ContainerHost> result = new HashSet<>();

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            result.addAll( resourceHost.getContainerHostsByOwnerId( ownerId ) );
        }

        return result;
    }


    @PermitAll
    @Override
    public ContainerHost getContainerHostByName( String hostname ) throws HostNotFoundException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ), "Container hostname shouldn't be null" );

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            try
            {
                return resourceHost.getContainerHostByName( hostname );
            }
            catch ( HostNotFoundException ignore )
            {
                //ignore
            }
        }

        throw new HostNotFoundException( String.format( "No container host found for name %s", hostname ) );
    }


    @PermitAll
    @Override
    public ContainerHost getContainerHostById( final String hostId ) throws HostNotFoundException
    {
        Preconditions.checkNotNull( hostId, "Invalid container host id" );

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            try
            {
                return resourceHost.getContainerHostById( hostId );
            }
            catch ( HostNotFoundException e )
            {
                //ignore
            }
        }

        throw new HostNotFoundException( String.format( "Container host not found by id %s", hostId ) );
    }


    @PermitAll
    @Override
    public ContainerHostInfo getContainerHostInfoById( final String containerHostId ) throws PeerException
    {
        ContainerHost containerHost = getContainerHostById( containerHostId );

        return new ContainerHostInfoModel( containerHost );
    }


    @PermitAll
    @Override
    public ResourceHost getResourceHostByName( String hostname ) throws HostNotFoundException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ), "Invalid resource host hostname" );


        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            if ( resourceHost.getHostname().equalsIgnoreCase( hostname ) )
            {
                return resourceHost;
            }
        }
        throw new HostNotFoundException( String.format( "Resource host not found by hostname %s", hostname ) );
    }


    @PermitAll
    @Override
    public ResourceHost getResourceHostById( final String hostId ) throws HostNotFoundException
    {
        Preconditions.checkNotNull( hostId, "Resource host id is null" );

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            if ( resourceHost.getId().equals( hostId ) )
            {
                return resourceHost;
            }
        }
        throw new HostNotFoundException( String.format( "Resource host not found by id %s", hostId ) );
    }


    @PermitAll
    @Override
    public ResourceHost getResourceHostByContainerName( final String containerName ) throws HostNotFoundException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( containerName ), "Invalid container name" );

        ContainerHost c = getContainerHostByName( containerName );
        ContainerHostEntity containerHostEntity = ( ContainerHostEntity ) c;
        return containerHostEntity.getParent();
    }


    @PermitAll
    @Override
    public ResourceHost getResourceHostByContainerId( final String hostId ) throws HostNotFoundException
    {
        Preconditions.checkNotNull( hostId, "Container host id is invalid" );

        ContainerHost c = getContainerHostById( hostId );
        ContainerHostEntity containerHostEntity = ( ContainerHostEntity ) c;
        return containerHostEntity.getParent();
    }


    @Override
    public Host bindHost( String id ) throws HostNotFoundException
    {
        Preconditions.checkNotNull( id );

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            if ( resourceHost.getId().equals( id ) )
            {
                return resourceHost;
            }
            else
            {
                try
                {
                    return resourceHost.getContainerHostById( id );
                }
                catch ( HostNotFoundException ignore )
                {
                    //ignore
                }
            }
        }

        throw new HostNotFoundException( String.format( "Host by id %s is not registered", id ) );
    }


    @Override
    public ContainerHostEntity bindHost( final ContainerId containerId ) throws HostNotFoundException
    {
        return ( ContainerHostEntity ) bindHost( containerId.getId() );
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void startContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Cannot operate on null container id" );

        ContainerHostEntity containerHost = bindHost( containerId );
        ResourceHost resourceHost = containerHost.getParent();
        try
        {
            resourceHost.startContainerHost( containerHost );
        }
        catch ( Exception e )
        {
            throw new PeerException( String.format( "Could not start LXC container [%s]", e.toString() ) );
        }
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void stopContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Cannot operate on null container id" );

        ContainerHostEntity containerHost = bindHost( containerId );
        ResourceHost resourceHost = containerHost.getParent();
        try
        {
            resourceHost.stopContainerHost( containerHost );
        }
        catch ( Exception e )
        {
            throw new PeerException( String.format( "Could not stop LXC container [%s]", e.toString() ) );
        }
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Delete" )
    @Override
    public void destroyContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Cannot operate on null container id" );

        ContainerHostEntity host = bindHost( containerId );
        ResourceHost resourceHost = host.getParent();

        try
        {
            resourceHost.destroyContainerHost( host );
            quotaManager.removeQuota( containerId );
        }
        catch ( ResourceHostException e )
        {
            String errMsg = String.format( "Could not destroy container [%s]", host.getHostname() );
            LOG.error( errMsg, e );
            throw new PeerException( errMsg, e.toString() );
        }

        resourceHostDataService.update( ( ResourceHostEntity ) resourceHost );
    }


    @Override
    public boolean isConnected( final HostId hostId )
    {
        Preconditions.checkNotNull( hostId, "Host id null" );

        try
        {
            HostInfo hostInfo = hostRegistry.getHostInfoById( hostId.getId() );
            return hostInfo.getId().equals( hostId.getId() );
        }
        catch ( HostDisconnectedException e )
        {
            return false;
        }
    }


    @Override
    public ResourceHost getManagementHost() throws HostNotFoundException
    {
        if ( managementHost == null )
        {
            throw new HostNotFoundException( String.format( "Management host not found on peer %s.", getId() ) );
        }

        return managementHost;
    }


    @Override
    public Set<ResourceHost> getResourceHosts()
    {
        synchronized ( resourceHosts )
        {
            return Sets.newConcurrentHashSet( this.resourceHosts );
        }
    }


    public void addResourceHost( final ResourceHost host )
    {
        Preconditions.checkNotNull( host, "Resource host could not be null." );

        synchronized ( resourceHosts )
        {
            resourceHosts.add( host );
        }
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder, final Host host ) throws CommandException
    {
        return execute( requestBuilder, host, null );
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder, final Host aHost,
                                  final CommandCallback callback ) throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder, "Invalid request" );
        Preconditions.checkNotNull( aHost, "Invalid host" );

        CommandResult result;

        if ( callback == null )
        {
            result = commandExecutor.execute( aHost.getId(), requestBuilder );
        }
        else
        {
            result = commandExecutor.execute( aHost.getId(), requestBuilder, callback );
        }

        return result;
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder, final Host aHost, final CommandCallback callback )
            throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder, "Invalid request" );
        Preconditions.checkNotNull( aHost, "Invalid host" );

        if ( callback == null )
        {
            commandExecutor.executeAsync( aHost.getId(), requestBuilder );
        }
        else
        {
            commandExecutor.executeAsync( aHost.getId(), requestBuilder, callback );
        }
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder, final Host host ) throws CommandException
    {
        executeAsync( requestBuilder, host, null );
    }


    @Override
    public boolean isLocal()
    {
        return true;
    }


    @Override
    public TemplateKurjun getTemplate( final String templateName )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( templateName ), "Invalid template name" );

        return templateRegistry.getTemplate( templateName );
    }


    @PermitAll
    @Override
    public boolean isOnline()
    {
        return true;
    }


    @Override
    public <T, V> V sendRequest( final T request, final String recipient, final int requestTimeout,
                                 final Class<V> responseType, final int responseTimeout, Map<String, String> headers )
            throws PeerException
    {
        Preconditions.checkNotNull( responseType, "Invalid response type" );

        return sendRequestInternal( request, recipient, responseType );
    }


    @Override
    public <T> void sendRequest( final T request, final String recipient, final int requestTimeout,
                                 Map<String, String> headers ) throws PeerException
    {
        sendRequestInternal( request, recipient, null );
    }


    protected <T, V> V sendRequestInternal( final T request, final String recipient, final Class<V> responseType )
            throws PeerException
    {
        Preconditions.checkNotNull( request, "Invalid request" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( recipient ), "Invalid recipient" );

        for ( RequestListener requestListener : requestListeners )
        {
            if ( recipient.equalsIgnoreCase( requestListener.getRecipient() ) )
            {
                try
                {
                    Object response = requestListener.onRequest( new Payload( request, getId() ) );

                    if ( response != null && responseType != null )
                    {
                        return responseType.cast( response );
                    }
                }
                catch ( Exception e )
                {
                    throw new PeerException( e );
                }
            }
        }

        return null;
    }


    @Override
    public void onHeartbeat( final ResourceHostInfo resourceHostInfo, Set<QuotaAlertValue> alerts )
    {
        LOG.debug( "On heartbeat: " + resourceHostInfo.getHostname() );
        if ( initialized )
        {
            ResourceHostEntity host;
            try
            {
                host = ( ResourceHostEntity ) getResourceHostByName( resourceHostInfo.getHostname() );
            }
            catch ( HostNotFoundException e )
            {
                host = new ResourceHostEntity( getId(), resourceHostInfo );
                resourceHostDataService.persist( host );
                addResourceHost( host );
                Set<ResourceHost> a = Sets.newHashSet();
                a.add( host );
                setResourceHostTransientFields( a );
                LOG.debug( String.format( "Resource host %s registered.", resourceHostInfo.getHostname() ) );
            }
            if ( host.updateHostInfo( resourceHostInfo ) )
            {
                resourceHostDataService.update( host );
                LOG.debug( String.format( "Resource host %s updated.", resourceHostInfo.getHostname() ) );
            }
            if ( managementHost == null )
            {
                try
                {
                    final Host managementLxc = findHostByName( "management" );
                    if ( managementLxc instanceof ContainerHostEntity )
                    {
                        managementHost = ( ( ContainerHostEntity ) managementLxc ).getParent();

                        //todo save flag that exchange happened to db
                        exchangeMhKeysWithRH();
                    }
                }
                catch ( Exception e )
                {
                    //ignore
                }
            }
        }
    }


    @Override
    public void exchangeMhKeysWithRH() throws Exception
    {

        RegistrationManager registrationManager = ServiceLocator.getServiceNoCache( RegistrationManager.class );

        String token = registrationManager.generateContainerTTLToken( 30 * 1000L ).getToken();

        final RequestBuilder requestBuilder =
                new RequestBuilder( String.format( "subutai import management -t %s", token ) );

        commandUtil.execute( requestBuilder, getManagementHost() );
    }


    @Override
    public ProcessResourceUsage getProcessResourceUsage( final ContainerId containerId, int pid ) throws PeerException
    {
        Preconditions.checkNotNull( containerId );
        Preconditions.checkArgument( pid > 0, "Process pid must be greater than 0" );

        try
        {
            return monitor.getProcessResourceUsage( containerId, pid );
        }
        catch ( MonitorException e )
        {
            throw new PeerException( e );
        }
    }


    @Override
    public Set<Integer> getCpuSet( final ContainerHost host ) throws PeerException
    {
        Preconditions.checkNotNull( host, "Invalid container host" );

        try
        {
            return quotaManager.getCpuSet( host.getContainerId() );
        }
        catch ( QuotaException e )
        {
            throw new PeerException( e );
        }
    }


    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void setCpuSet( final ContainerHost host, final Set<Integer> cpuSet ) throws PeerException
    {
        Preconditions.checkNotNull( host, "Invalid container host" );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( cpuSet ), "Empty cpu set" );

        try
        {
            quotaManager.setCpuSet( host.getContainerId(), cpuSet );
        }
        catch ( QuotaException e )
        {
            throw new PeerException( e );
        }
    }


    //networking


    @Override
    public String getVniDomain( final Long vni ) throws PeerException
    {
        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                return getNetworkManager().getVlanDomain( reservedNetworkResource.getVlan() );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException(
                        String.format( "Error obtaining domain by vlan %d", reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    //    @RolesAllowed( "Environment-Management|Delete" )
    @Override
    public void removeVniDomain( final Long vni ) throws PeerException
    {
        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                getNetworkManager().removeVlanDomain( reservedNetworkResource.getVlan() );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException(
                        String.format( "Error removing domain by vlan %d", reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void setVniDomain( final Long vni, final String domain,
                              final DomainLoadBalanceStrategy domainLoadBalanceStrategy, final String sslCertPath )
            throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( domain ) );
        Preconditions.checkNotNull( domainLoadBalanceStrategy );

        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                getNetworkManager().setVlanDomain( reservedNetworkResource.getVlan(), domain, domainLoadBalanceStrategy,
                        sslCertPath );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException(
                        String.format( "Error setting domain by vlan %d", reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    @Override
    public boolean isIpInVniDomain( final String hostIp, final Long vni ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostIp ) );

        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                return getNetworkManager().isIpInVlanDomain( hostIp, reservedNetworkResource.getVlan() );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException( String.format( "Error checking domain by ip %s and vlan %d", hostIp,
                        reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void addIpToVniDomain( final String hostIp, final Long vni ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostIp ) );

        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                getNetworkManager().addIpToVlanDomain( hostIp, reservedNetworkResource.getVlan() );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException( String.format( "Error adding ip %s to domain by vlan %d", hostIp,
                        reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void removeIpFromVniDomain( final String hostIp, final Long vni ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostIp ) );

        NetworkResource reservedNetworkResource = getReservedNetworkResources().findByVni( vni );

        if ( reservedNetworkResource != null )
        {
            try
            {
                getNetworkManager().removeIpFromVlanDomain( hostIp, reservedNetworkResource.getVlan() );
            }
            catch ( NetworkManagerException e )
            {
                throw new PeerException( String.format( "Error removing ip %s from domain by vlan %d", hostIp,
                        reservedNetworkResource.getVlan() ), e );
            }
        }
        else
        {

            throw new PeerException( String.format( "Vlan for vni %d not found", vni ) );
        }
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public int setupSshTunnelForContainer( final String containerIp, final int sshIdleTimeout ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( containerIp ) );
        Preconditions.checkArgument( containerIp.matches( Common.IP_REGEX ) );
        Preconditions.checkArgument( sshIdleTimeout > 0 );


        try
        {
            return getNetworkManager().setupContainerSsh( containerIp, sshIdleTimeout );
        }
        catch ( NetworkManagerException e )
        {
            throw new PeerException( String.format( "Error setting up ssh tunnel for container ip %s", containerIp ),
                    e );
        }
    }


    @Override
    public List<ContainerHost> getPeerContainers( final String peerId )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( peerId ) );

        List<ContainerHost> result = new ArrayList<>();
        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            result.addAll( resourceHost.getContainerHostsByPeerId( peerId ) );
        }
        return result;
    }


    @Override
    public void addRequestListener( final RequestListener listener )
    {
        if ( listener != null )
        {
            requestListeners.add( listener );
        }
    }


    @Override
    public void removeRequestListener( final RequestListener listener )
    {
        if ( listener != null )
        {
            requestListeners.remove( listener );
        }
    }


    @Override
    public Set<RequestListener> getRequestListeners()
    {
        return Collections.unmodifiableSet( requestListeners );
    }


    /* ***********************************************
     *  Create PEK
     */
    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public PublicKeyContainer createPeerEnvironmentKeyPair( EnvironmentId envId ) throws PeerException
    {
        Preconditions.checkNotNull( envId );
        //TODO don't generate PEK if already exists, return the existing one!!!
        KeyManager keyManager = securityManager.getKeyManager();
        EncryptionTool encTool = securityManager.getEncryptionTool();
        String pairId = String.format( "%s-%s", getId(), envId.getId() );
        final PGPSecretKeyRing peerSecKeyRing = securityManager.getKeyManager().getSecretKeyRing( null );
        try
        {
            KeyPair keyPair = keyManager.generateKeyPair( pairId, false );

            //******Create PEK *****************************************************************
            PGPSecretKeyRing secRing = PGPKeyUtil.readSecretKeyRing( keyPair.getSecKeyring() );
            PGPPublicKeyRing pubRing = PGPKeyUtil.readPublicKeyRing( keyPair.getPubKeyring() );

            //***************Save Keys *********************************************************
            keyManager.saveSecretKeyRing( pairId, SecurityKeyType.PeerEnvironmentKey.getId(), secRing );
            keyManager.savePublicKeyRing( pairId, SecurityKeyType.PeerEnvironmentKey.getId(), pubRing );

            pubRing =
                    securityManager.getKeyManager().setKeyTrust( peerSecKeyRing, pubRing, KeyTrustLevel.Full.getId() );

            return new PublicKeyContainer( getId(), pubRing.getPublicKey().getFingerprint(),
                    encTool.armorByteArrayToString( pubRing.getEncoded() ) );
        }
        catch ( IOException | PGPException ex )
        {
            throw new PeerException( ex );
        }
    }


    @Override
    public void updatePeerEnvironmentPubKey( final EnvironmentId environmentId, final PGPPublicKeyRing pubKeyRing )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId );
        Preconditions.checkNotNull( pubKeyRing );

        securityManager.getKeyManager().updatePublicKeyRing( pubKeyRing );
    }


    @Override
    public void addPeerEnvironmentPubKey( final String keyId, final PGPPublicKeyRing pubRing )
    {
        Preconditions.checkNotNull( keyId );
        Preconditions.checkNotNull( pubRing );

        securityManager.getKeyManager().savePublicKeyRing( keyId, SecurityKeyType.PeerEnvironmentKey.getId(), pubRing );
    }


    @Override
    public HostInterfaces getInterfaces() throws HostNotFoundException
    {
        return getManagementHost().getHostInterfaces();
    }


    @Override
    public synchronized void reserveNetworkResource( final NetworkResourceImpl networkResource ) throws PeerException
    {

        Preconditions.checkNotNull( networkResource );

        try
        {
            NetworkResource nr = networkResourceDao.find( networkResource );

            if ( nr != null )
            {
                throw new PeerException( String.format( "Network resource %s is already reserved", nr ) );
            }
            else
            {
                UsedNetworkResources usedNetworkResources = getUsedNetworkResources();

                if ( usedNetworkResources.containerSubnetExists( networkResource.getContainerSubnet() ) )
                {
                    throw new PeerException( String.format( "Container subnet %s is already reserved",
                            networkResource.getContainerSubnet() ) );
                }
                if ( usedNetworkResources.p2pSubnetExists( networkResource.getP2pSubnet() ) )
                {
                    throw new PeerException(
                            String.format( "P2P subnet %s is already reserved", networkResource.getP2pSubnet() ) );
                }
                if ( usedNetworkResources.vniExists( networkResource.getVni() ) )
                {
                    throw new PeerException( String.format( "VNI %d is already reserved", networkResource.getVni() ) );
                }

                //calculate free vlan for this environment
                int freeVlan = usedNetworkResources.calculateFreeVlan();
                if ( freeVlan == -1 )
                {
                    throw new PeerException( "No free VLANs slots are left" );
                }

                networkResourceDao.create( new NetworkResourceEntity( networkResource, freeVlan ) );
            }
        }
        catch ( DaoException e )
        {
            throw new PeerException( "Error reserving network resources", e );
        }
    }


    @Override
    public ReservedNetworkResources getReservedNetworkResources() throws PeerException
    {
        ReservedNetworkResources reservedNetworkResources = new ReservedNetworkResources();

        try
        {
            for ( NetworkResource networkResource : networkResourceDao.readAll() )
            {
                reservedNetworkResources.addNetworkResource( networkResource );
            }
        }
        catch ( DaoException e )
        {
            throw new PeerException( "Error getting reserved network resources", e );
        }

        return reservedNetworkResources;
    }


    @Override
    public UsedNetworkResources getUsedNetworkResources() throws PeerException
    {
        final UsedNetworkResources usedNetworkResources = new UsedNetworkResources();

        Set<ResourceHost> resourceHosts = getResourceHosts();
        ExecutorService executorService = Executors.newFixedThreadPool( resourceHosts.size() );
        ExecutorCompletionService<Object> completionService = new ExecutorCompletionService<>( executorService );

        for ( final ResourceHost resourceHost : resourceHosts )
        {
            completionService.submit( new Callable<Object>()
            {
                @Override
                public Object call() throws Exception
                {

                    //tunnels
                    Tunnels tunnels = resourceHost.getTunnels();
                    for ( Tunnel tunnel : tunnels.getTunnels() )
                    {
                        usedNetworkResources.addVni( tunnel.getVni() );
                        usedNetworkResources.addVlan( tunnel.getVlan() );
                        usedNetworkResources.addP2pSubnet( tunnel.getTunnelIp() );
                    }

                    //p2p connections
                    P2PConnections p2PConnections = resourceHost.getP2PConnections();
                    for ( P2PConnection p2PConnection : p2PConnections.getConnections() )
                    {
                        usedNetworkResources.addP2pSubnet( p2PConnection.getIp() );
                    }

                    for ( HostInterface iface : resourceHost.getHostInterfaces().getAll() )
                    {
                        //container subnet
                        Matcher matcher = GATEWAY_INTERFACE_NAME_PATTERN.matcher( iface.getName().trim() );
                        if ( matcher.find() )
                        {
                            usedNetworkResources.addContainerSubnet( iface.getIp() );
                            usedNetworkResources.addVlan( Integer.parseInt( matcher.group( 1 ) ) );
                        }

                        //p2p subnet
                        matcher = P2P_INTERFACE_NAME_PATTERN.matcher( iface.getName().trim() );
                        if ( matcher.find() )
                        {
                            usedNetworkResources.addP2pSubnet( iface.getIp() );
                            usedNetworkResources.addVlan( Integer.parseInt( matcher.group( 1 ) ) );
                        }

                        //add LAN subnet to prevent collisions
                        if ( iface.getName().equalsIgnoreCase( SystemSettings.getExternalIpInterface() ) )
                        {
                            usedNetworkResources.addContainerSubnet( iface.getIp() );
                            usedNetworkResources.addP2pSubnet( iface.getIp() );
                        }
                    }

                    return null;
                }
            } );
        }

        executorService.shutdown();

        try
        {
            for ( final ResourceHost ignored : resourceHosts )
            {
                completionService.take().get();
            }
        }
        catch ( Exception e )
        {
            throw new PeerException( "Error gathering reserved net resources", e );
        }

        //add reserved ones too
        for ( NetworkResource networkResource : getReservedNetworkResources().getNetworkResources() )
        {
            usedNetworkResources.addVni( networkResource.getVni() );
            usedNetworkResources.addVlan( networkResource.getVlan() );
            usedNetworkResources.addContainerSubnet( networkResource.getContainerSubnet() );
            usedNetworkResources.addP2pSubnet( networkResource.getP2pSubnet() );
        }


        return usedNetworkResources;
    }


    //TODO this is for basic environment via hub
    //@RolesAllowed( "Environment-Management|Write" )
    @Override
    public void setupTunnels( final P2pIps p2pIps, final String environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( p2pIps, "Invalid peer ips set" );
        Preconditions.checkNotNull( environmentId, "Invalid environment id" );

        final NetworkResource networkResource = getReservedNetworkResources().findByEnvironmentId( environmentId );

        if ( networkResource == null )
        {
            throw new PeerException(
                    String.format( "No reserved network resources found for environment %s", environmentId ) );
        }

        Set<ResourceHost> resourceHosts = getResourceHosts();
        ExecutorService executorService = Executors.newFixedThreadPool( resourceHosts.size() );
        ExecutorCompletionService<Object> completionService = new ExecutorCompletionService<>( executorService );

        int taskCount = 0;
        for ( final ResourceHost resourceHost : resourceHosts )
        {
            //setup tunnel only if this RH participates in the swarm
            if ( p2pIps.findByRhId( resourceHost.getId() ) != null )
            {
                taskCount++;
                completionService.submit( new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        resourceHost.setupTunnels( p2pIps, networkResource );

                        return null;
                    }
                } );
            }
        }


        executorService.shutdown();

        try
        {
            for ( int i = 0; i < taskCount; i++ )
            {
                completionService.take().get();
            }
        }
        catch ( Exception e )
        {
            throw new PeerException( "Error setting up tunnels", e );
        }
    }


    //----------- P2P SECTION BEGIN --------------------
    @Override
    public void resetSwarmSecretKey( final P2PCredentials p2PCredentials ) throws PeerException
    {

        Preconditions.checkNotNull( p2PCredentials, "Invalid p2p credentials" );

        Set<ResourceHost> resourceHosts = getResourceHosts();
        ExecutorService executorService = Executors.newFixedThreadPool( resourceHosts.size() );
        ExecutorCompletionService<Object> completionService = new ExecutorCompletionService<>( executorService );

        for ( final ResourceHost resourceHost : resourceHosts )
        {
            completionService.submit( new Callable<Object>()
            {
                @Override
                public Object call() throws Exception
                {

                    resourceHost.resetSwarmSecretKey( p2PCredentials.getP2pHash(), p2PCredentials.getP2pSecretKey(),
                            p2PCredentials.getP2pTtlSeconds() );

                    return null;
                }
            } );
        }

        executorService.shutdown();

        try
        {
            for ( final ResourceHost ignored : resourceHosts )
            {
                completionService.take().get();
            }
        }
        catch ( Exception e )
        {
            throw new PeerException( "Error resetting P2P secret key", e );
        }
    }


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void joinP2PSwarm( final P2PConfig config ) throws PeerException
    {
        Preconditions.checkNotNull( config, "Invalid p2p config" );

        LOG.debug( String.format( "Joining P2P swarm: %s", config.getHash() ) );

        try
        {
            NetworkResource reservedNetworkResource =
                    getReservedNetworkResources().findByEnvironmentId( config.getEnvironmentId() );

            if ( reservedNetworkResource == null )
            {
                throw new PeerException(
                        String.format( "Reserved vni not found for environment %s", config.getEnvironmentId() ) );
            }

            final String p2pInterface = P2PUtil.generateInterfaceName( reservedNetworkResource.getVlan() );

            ExecutorService executorService = Executors.newFixedThreadPool( config.getRhP2pIps().size() );
            ExecutorCompletionService<Object> completionService = new ExecutorCompletionService<>( executorService );

            for ( final RhP2pIp rhP2pIp : config.getRhP2pIps() )
            {
                final ResourceHost resourceHost = getResourceHostById( rhP2pIp.getRhId() );

                completionService.submit( new Callable<Object>()
                {
                    @Override
                    public P2PConnection call() throws Exception
                    {

                        resourceHost.joinP2PSwarm( rhP2pIp.getP2pIp(), p2pInterface, config.getHash(),
                                config.getSecretKey(), config.getSecretKeyTtlSec() );


                        return null;
                    }
                } );
            }

            executorService.shutdown();

            for ( RhP2pIp ignored : config.getRhP2pIps() )
            {
                completionService.take().get();
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( "Failed to join P2P swarm", e );
        }
    }


    @Override
    public void joinOrUpdateP2PSwarm( final P2PConfig config ) throws PeerException
    {
        //for existing rhp2pip call joinswarm, for missing call resetswarmkey
        Preconditions.checkNotNull( config, "Invalid p2p config" );

        LOG.debug( String.format( "Joining/updating P2P swarm: %s", config.getHash() ) );

        try
        {
            NetworkResource reservedNetworkResource =
                    getReservedNetworkResources().findByEnvironmentId( config.getEnvironmentId() );

            if ( reservedNetworkResource == null )
            {
                throw new PeerException(
                        String.format( "Reserved vni not found for environment %s", config.getEnvironmentId() ) );
            }

            final String p2pInterface = P2PUtil.generateInterfaceName( reservedNetworkResource.getVlan() );

            Set<ResourceHost> resourceHosts = getResourceHosts();
            ExecutorService executorService = Executors.newFixedThreadPool( resourceHosts.size() );
            ExecutorCompletionService<Object> completionService = new ExecutorCompletionService<>( executorService );

            for ( final ResourceHost resourceHost : resourceHosts )
            {
                final RhP2pIp rhP2pIp = config.findByRhId( resourceHost.getId() );

                if ( rhP2pIp != null )
                {
                    //try to join RH (updates if already participating)
                    completionService.submit( new Callable<Object>()
                    {
                        @Override
                        public P2PConnection call() throws Exception
                        {

                            resourceHost.joinP2PSwarm( rhP2pIp.getP2pIp(), p2pInterface, config.getHash(),
                                    config.getSecretKey(), config.getSecretKeyTtlSec() );


                            return null;
                        }
                    } );
                }
                else
                {
                    //try to update missing RH in case it participates in the swarm
                    completionService.submit( new Callable<Object>()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            resourceHost.resetSwarmSecretKey( config.getHash(), config.getSecretKey(),
                                    config.getSecretKeyTtlSec() );
                            return null;
                        }
                    } );
                }
            }

            executorService.shutdown();

            for ( ResourceHost ignored : resourceHosts )
            {
                completionService.take().get();
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( "Failed to join/update P2P swarm", e );
        }
    }


    @Deprecated
    public void createP2PSwarm( final P2PConfig config ) throws PeerException
    {
        ///no-op
    }


    //----------- P2P SECTION END --------------------


    //TODO this is for basic environment via hub
    //    @RolesAllowed( "Environment-Management|Delete" )
    @Override
    public void cleanupEnvironment( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId );

        final NetworkResource reservedNetworkResource =
                getReservedNetworkResources().findByEnvironmentId( environmentId.getId() );

        if ( reservedNetworkResource == null )
        {
            LOG.warn( "Network reservation for environment {} not found", environmentId.getId() );
            return;
        }

        Set<ResourceHost> resourceHosts = getResourceHosts();

        ExecutorService executorService = Executors.newFixedThreadPool( resourceHosts.size() );

        for ( final ResourceHost resourceHost : getResourceHosts() )
        {
            executorService.submit( new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        resourceHost.cleanup( environmentId, reservedNetworkResource.getVlan() );
                    }
                    catch ( ResourceHostException e )
                    {
                        LOG.error( "Failed to cleanup environment {} on RH {}", environmentId.getId(),
                                resourceHost.getId(), e );
                    }
                }
            } );
        }

        executorService.shutdown();

        //remove reservation
        try
        {
            networkResourceDao.delete( ( NetworkResourceEntity ) reservedNetworkResource );
        }
        catch ( DaoException e )
        {
            LOG.error( "Failed to delete network reservation for environment {}", environmentId.getId(), e );
        }

        //remove PEK
        try
        {
            KeyManager keyManager = securityManager.getKeyManager();

            keyManager.removeKeyData( environmentId.getId() );
            keyManager.removeKeyData( getId() + "-" + environmentId.getId() );
        }
        catch ( Exception e )
        {
            LOG.error( "Failed to delete PEK for environment {}", environmentId.getId(), e );
        }
    }


    @Override
    public ResourceHostMetrics getResourceHostMetrics()
    {
        return monitor.getResourceHostMetrics();
    }


    @Override
    public PeerResources getResourceLimits( final String peerId ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( peerId ) );

        return quotaManager.getResourceLimits( peerId );
    }


    @Override
    public List<TemplateKurjun> getTemplates()
    {
        return templateRegistry.list();
    }


    @Override
    public TemplateKurjun getTemplateByName( final String name )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( name ) );

        return templateRegistry.getTemplate( name );
    }


    @Override
    public ContainerQuota getAvailableQuota( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId );

        try
        {
            ContainerHost containerHost = getContainerHostById( containerId.getId() );
            return quotaManager.getAvailableQuota( containerHost.getContainerId() );
        }
        catch ( QuotaException e )
        {
            throw new PeerException( String.format( "Could not obtain quota for: %s", containerId ) );
        }
    }


    @Override
    public ContainerQuota getQuota( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId );
        try
        {
            ContainerHost containerHost = getContainerHostById( containerId.getId() );
            return quotaManager.getQuota( containerHost.getContainerId() );
        }
        catch ( QuotaException e )
        {
            throw new PeerException( String.format( "Could not obtain quota for: %s.", containerId.getId() ) );
        }
    }


    @Override
    public void setQuota( final ContainerId containerId, final ContainerQuota containerQuota ) throws PeerException
    {
        Preconditions.checkNotNull( containerId );
        Preconditions.checkNotNull( containerQuota );
        try
        {
            ContainerHost containerHost = getContainerHostById( containerId.getId() );
            quotaManager.setQuota( containerHost.getContainerId(), containerQuota );
        }
        catch ( QuotaException e )
        {
            throw new PeerException( String.format( "Could not set quota for: %s", containerId.getId() ) );
        }
    }


    @Override
    public void alert( AlertEvent alert )
    {
        Preconditions.checkNotNull( alert );

        monitor.addAlert( alert );
    }


    @Override
    public HistoricalMetrics getHistoricalMetrics( final String hostname, final Date startTime, final Date endTime )
            throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ) );
        Preconditions.checkNotNull( startTime );
        Preconditions.checkNotNull( endTime );

        try
        {
            Host host = findHostByName( hostname );
            return monitor.getHistoricalMetrics( host, startTime, endTime );
        }
        catch ( HostNotFoundException e )
        {
            throw new PeerException( e.getMessage(), e );
        }
    }


    @Override
    public Host findHostByName( final String hostname ) throws HostNotFoundException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ) );


        if ( managementHost != null && getManagementHost().getHostname().equals( hostname ) )
        {
            return managementHost;
        }

        for ( ResourceHost resourceHost : getResourceHosts() )
        {
            if ( resourceHost.getHostname().equals( hostname ) )
            {
                return resourceHost;
            }
            for ( ContainerHost containerHost : resourceHost.getContainerHosts() )
            {
                if ( containerHost.getHostname().equals( hostname ) )
                {
                    return containerHost;
                }
            }
        }

        throw new HostNotFoundException( "Host by name '" + hostname + "' not found." );
    }


    public <T> Future<T> queueSequentialTask( Callable<T> callable )
    {
        Preconditions.checkNotNull( callable );

        return singleThreadExecutorService.submit( callable );
    }


    protected NetworkManager getNetworkManager() throws PeerException
    {
        try
        {
            return serviceLocator.getService( NetworkManager.class );
        }
        catch ( NamingException e )
        {
            throw new PeerException( e );
        }
    }


    @Override
    public List<Task> getTaskList()
    {
        return taskManager.getAllTasks();
    }


    @Override
    public Task getTask( final Integer id )
    {
        return taskManager.getTask( id );
    }


    @Override
    public String getExternalIp() throws PeerException
    {
        return getPeerInfo().getIp();
    }


    @Override
    public HostId getResourceHostIdByContainerId( final ContainerId id ) throws PeerException
    {
        return new HostId( getResourceHostByContainerId( id.getId() ).getId() );
    }


    @Override
    @Deprecated
    public PingDistances getP2PSwarmDistances( final String p2pHash, final Integer maxAddress ) throws PeerException
    {
        PingDistances result = new PingDistances();
        try
        {
            final P2PConnections p2PConnections = getNetworkManager().getP2PConnections( getManagementHost() );

            final P2PConnection p2PConnection = p2PConnections.findByHash( p2pHash );

            if ( p2PConnection == null )
            {
                return result;
            }
            String p2pIP = p2PConnection.getIp();
            final SubnetUtils.SubnetInfo info = new SubnetUtils( p2pIP, ControlNetworkUtil.NETWORK_MASK ).getInfo();

            ExecutorService pool = Executors.newCachedThreadPool();
            ExecutorCompletionService<PingDistance> completionService = new ExecutorCompletionService<>( pool );
            int counter = 0;
            for ( int i = 0; i < maxAddress; i++ )
            {
                if ( !p2PConnection.getIp().equals( info.getAllAddresses()[i] ) )
                {
                    completionService
                            .submit( new PingDistanceTask( p2PConnection.getIp(), info.getAllAddresses()[i] ) );
                    counter++;
                }
            }

            pool.shutdown();

            while ( counter-- > 0 )
            {
                try
                {
                    Future<PingDistance> d = completionService.take();
                    result.add( d.get() );
                }
                catch ( ExecutionException | InterruptedException e )
                {
                    // ignore
                }
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
            throw new PeerException( e.getMessage() );
        }
        return result;
    }


    private class PingDistanceTask implements Callable<PingDistance>
    {
        private final String sourceIp;
        private final String targetIp;


        public PingDistanceTask( final String sourceIp, final String targetIp )
        {
            this.sourceIp = sourceIp;
            this.targetIp = targetIp;
        }


        @Override
        public PingDistance call() throws Exception
        {
            try
            {
                return getNetworkManager().getPingDistance( getManagementHost(), sourceIp, targetIp );
            }
            catch ( Exception e )
            {
                return new PingDistance( sourceIp, targetIp, null, null, null, null );
            }
        }
    }


    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof LocalPeerImpl ) )
        {
            return false;
        }

        final LocalPeerImpl that = ( LocalPeerImpl ) o;

        return getId().equals( that.getId() );
    }


    @Override
    public int hashCode()
    {
        return getId().hashCode();
    }
}

