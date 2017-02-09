package io.subutai.core.environment.rest.ui;


import java.io.File;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.subutai.common.environment.ContainerDto;
import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentCreationRef;
import io.subutai.common.environment.EnvironmentDto;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.HubEnvironment;
import io.subutai.common.environment.Node;
import io.subutai.common.environment.NodeSchema;
import io.subutai.common.environment.PeerTemplatesDownloadProgress;
import io.subutai.common.environment.Topology;
import io.subutai.common.gson.required.RequiredDeserializer;
import io.subutai.common.metric.ResourceHostMetric;
import io.subutai.common.network.ProxyLoadBalanceStrategy;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.protocol.Template;
import io.subutai.common.settings.Common;
import io.subutai.common.util.JsonUtil;
import io.subutai.common.util.StringUtil;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.environment.api.SecureEnvironmentManager;
import io.subutai.core.environment.api.ShareDto.ShareDto;
import io.subutai.core.environment.api.exception.EnvironmentCreationException;
import io.subutai.core.environment.rest.ui.entity.PeerDto;
import io.subutai.core.environment.rest.ui.entity.ResourceHostDto;
import io.subutai.core.lxc.quota.api.QuotaManager;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.core.strategy.api.ContainerPlacementStrategy;
import io.subutai.core.strategy.api.RoundRobinStrategy;
import io.subutai.core.strategy.api.StrategyManager;
import io.subutai.core.template.api.TemplateManager;
import io.subutai.hub.share.quota.ContainerQuota;
import io.subutai.hub.share.quota.ContainerSize;
import io.subutai.hub.share.resource.PeerGroupResources;


public class RestServiceImpl implements RestService
{
    private static final Logger LOG = LoggerFactory.getLogger( RestServiceImpl.class );
    private static final String ERROR_KEY = "ERROR";
    private final EnvironmentManager environmentManager;
    private final PeerManager peerManager;
    private final TemplateManager templateManager;
    private final StrategyManager strategyManager;
    private final QuotaManager quotaManager;
    private final SecureEnvironmentManager secureEnvironmentManager;
    private Gson gson = RequiredDeserializer.createValidatingGson();


    public RestServiceImpl( final EnvironmentManager environmentManager, final PeerManager peerManager,
                            final TemplateManager templateManager, final StrategyManager strategyManager,
                            final QuotaManager quotaManager, final SecureEnvironmentManager secureEnvironmentManager )
    {
        Preconditions.checkNotNull( environmentManager );
        Preconditions.checkNotNull( peerManager );
        Preconditions.checkNotNull( templateManager );
        Preconditions.checkNotNull( strategyManager );

        this.environmentManager = environmentManager;
        this.peerManager = peerManager;
        this.templateManager = templateManager;
        this.strategyManager = strategyManager;
        this.quotaManager = quotaManager;
        this.secureEnvironmentManager = secureEnvironmentManager;
    }


    /** Templates *************************************************** */

    @Override
    public Response listTemplates()
    {
        Set<Template> templates = templateManager.getTemplates().stream().filter(
                n -> !n.getName().equalsIgnoreCase( Common.MANAGEMENT_HOSTNAME ) ).filter( n -> !n.getName().matches(
                "(?i)cassandra14|" + "cassandra16|" + "elasticsearch14|" + "elasticsearch16|" + "hadoop14|"
                        + "hadoop16|" + "mongo14|" + "mongo16|" + "openjre714|" + "openjre716|" + "solr14|" + "solr16|"
                        + "storm14|" + "storm16|" + "zookeeper14|" + "zookeeper16" ) ).collect( Collectors.toSet() );

        return Response.ok().entity( gson.toJson( templates ) ).build();
    }


    @Override
    public Response listPrivateTemplates()
    {
        return Response.ok().entity( gson.toJson( templateManager.getUserPrivateTemplates() ) ).build();
    }


    @Override
    public Response getVerifiedTemplate( final String templateName )
    {
        try
        {
            Template template = templateManager.getVerifiedTemplateByName( templateName );

            if ( template != null )
            {
                return Response.ok( gson.toJson( template ) ).build();
            }
            else
            {
                return Response.status( Response.Status.NOT_FOUND ).build();
            }
        }
        catch ( Exception e )
        {
            return Response.serverError().entity(
                    JsonUtil.toJson( ERROR_KEY, e.getMessage() == null ? "Internal error" : e.getMessage() ) ).build();
        }
    }


    @Override
    public Response createTemplate( final String environmentId, final String containerId, final String templateName,
                                    final boolean privateTemplate )
    {

        try
        {
            return Response.ok( environmentManager
                    .createTemplate( environmentId, containerId, templateName, privateTemplate ) ).build();
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }
    }


    /** Environments **************************************************** */


    @Override
    public Response build( final String name, final String topologyJson )
    {

        Map<String, String> envCreationRef = Maps.newHashMap();

        try
        {
            Preconditions.checkArgument( !Strings.isNullOrEmpty( name ), "Invalid environment name" );
            Preconditions.checkArgument( !Strings.isNullOrEmpty( topologyJson ), "Invalid environment topology" );

            checkName( name );

            ContainerPlacementStrategy placementStrategy = strategyManager.findStrategyById( RoundRobinStrategy.ID );

            List<NodeSchema> schema = JsonUtil.fromJson( topologyJson, new TypeToken<List<NodeSchema>>()
            {
            }.getType() );

            final PeerGroupResources peerGroupResources = peerManager.getPeerGroupResources();
            final Map<ContainerSize, ContainerQuota> quotas = quotaManager.getDefaultQuotas();

            Topology topology = placementStrategy.distribute( name, schema, peerGroupResources, quotas );

            EnvironmentCreationRef ref = environmentManager.createEnvironment( topology, true );

            envCreationRef.put( "trackerId", ref.getTrackerId() );
            envCreationRef.put( "environmentId", ref.getEnvironmentId() );
        }
        catch ( Exception e )
        {
            if ( e.getClass() == AccessControlException.class )
            {
                LOG.error( e.getMessage() );
                return Response.status( Response.Status.INTERNAL_SERVER_ERROR ).
                        entity( JsonUtil.GSON.toJson( "You don't have permission to perform this operation" ) ).build();
            }

            return Response.serverError().entity(
                    JsonUtil.toJson( ERROR_KEY, e.getMessage() == null ? "Internal error" : e.getMessage() ) ).build();
        }

        return Response.ok( JsonUtil.toJson( envCreationRef ) ).build();
    }


    @Override
    public Response buildAdvanced( final String name, final String topologyJson )
    {
        Map<String, String> envCreationRef = Maps.newHashMap();

        try
        {
            checkName( name );

            List<Node> schema = JsonUtil.fromJson( topologyJson, new TypeToken<List<Node>>()
            {
            }.getType() );

            Topology topology = new Topology( name );

            schema.forEach( s ->
            {
                ContainerQuota defaultQuota = quotaManager.getDefaultContainerQuota( s.getQuota().getContainerSize() );
                if ( defaultQuota == null )
                {
                    // selected CUSTOM container size
                    // TODO: 1/30/17 set value from UI . as a workaround we set it to SMALL
                    defaultQuota = quotaManager.getDefaultContainerQuota( ContainerSize.SMALL );
                }
                s.getQuota().copyValues( defaultQuota );
                topology.addNodePlacement( s.getPeerId(), s );
            } );


            EnvironmentCreationRef ref = environmentManager.createEnvironment( topology, true );

            envCreationRef.put( "trackerId", ref.getTrackerId() );
            envCreationRef.put( "environmentId", ref.getEnvironmentId() );
        }
        catch ( Exception e )
        {
            if ( e.getClass() == AccessControlException.class )
            {
                LOG.error( e.getMessage() );
                return Response.status( Response.Status.INTERNAL_SERVER_ERROR ).
                        entity( JsonUtil.GSON.toJson( "You don't have permission to perform this operation" ) ).build();
            }

            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok( JsonUtil.toJson( envCreationRef ) ).build();
    }


    @Override
    public Response modify( final String environmentId, final String topologyJson, final String removedContainers,
                            final String quotaContainers )
    {
        String trackerId;

        try
        {
            String name = environmentManager.loadEnvironment( environmentId ).getName();

            ContainerPlacementStrategy placementStrategy = strategyManager.findStrategyById( RoundRobinStrategy.ID );


            List<NodeSchema> schema = JsonUtil.fromJson( topologyJson, new TypeToken<List<NodeSchema>>()
            {
            }.getType() );


            List<String> containers = JsonUtil.fromJson( removedContainers, new TypeToken<List<String>>()
            {
            }.getType() );


            Map<String, ContainerQuota> changedContainersFiltered = new HashMap<>();
            List<Map<String, String>> changingContainers =
                    JsonUtil.fromJson( quotaContainers, new TypeToken<List<Map<String, String>>>()
                    {
                    }.getType() );

            for ( Map<String, String> cont : changingContainers )
            {
                ContainerSize containerSize = ContainerSize.valueOf( cont.get( "value" ) );
                ContainerQuota defaultQuota = quotaManager.getDefaultContainerQuota( containerSize );
                if ( defaultQuota == null )
                {
                    // selected CUSTOM container size
                    // TODO: 1/30/17 set value from UI . as a workaround we set it to SMALL
                    defaultQuota = quotaManager.getDefaultContainerQuota( ContainerSize.SMALL );
                }

                changedContainersFiltered.put( cont.get( "key" ), defaultQuota );
            }


            Topology topology = null;
            if ( !schema.isEmpty() )
            {
                final PeerGroupResources peerGroupResources = peerManager.getPeerGroupResources();
                final Map<ContainerSize, ContainerQuota> quotas = quotaManager.getDefaultQuotas();

                topology = placementStrategy.distribute( name, schema, peerGroupResources, quotas );
            }

            EnvironmentCreationRef ref = environmentManager
                    .modifyEnvironment( environmentId, topology, containers, changedContainersFiltered, true );

            trackerId = ref.getTrackerId();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) )
                           .build();
        }

        return Response.ok( JsonUtil.toJson( trackerId ) ).build();
    }


    @Override
    public Response modifyAdvanced( final String environmentId, final String topologyJson,
                                    final String removedContainers, final String quotaContainers )
    {
        String trackerId;

        try
        {
            String name = environmentManager.loadEnvironment( environmentId ).getName();

            List<Node> schema = JsonUtil.fromJson( topologyJson, new TypeToken<List<Node>>()
            {
            }.getType() );

            List<String> containers = JsonUtil.fromJson( removedContainers, new TypeToken<List<String>>()
            {
            }.getType() );

            Map<String, ContainerQuota> changedContainersFiltered = new HashMap<>();
            List<Map<String, String>> changingContainers =
                    JsonUtil.fromJson( quotaContainers, new TypeToken<List<Map<String, String>>>()
                    {
                    }.getType() );

            for ( Map<String, String> cont : changingContainers )
            {
                ContainerSize containerSize = ContainerSize.valueOf( cont.get( "value" ) );
                ContainerQuota defaultQuota = quotaManager.getDefaultContainerQuota( containerSize );
                if ( defaultQuota == null )
                {
                    // selected CUSTOM container size
                    // TODO: 1/30/17 set value from UI . as a workaround we set it to SMALL
                    defaultQuota = quotaManager.getDefaultContainerQuota( ContainerSize.SMALL );
                }

                changedContainersFiltered.put( cont.get( "key" ), defaultQuota );
            }

            Topology topology = new Topology( name );

            schema.forEach( s -> topology.addNodePlacement( s.getPeerId(), s ) );

            EnvironmentCreationRef ref = environmentManager
                    .modifyEnvironment( environmentId, topology, containers, changedContainersFiltered, true );

            trackerId = ref.getTrackerId();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) )
                           .build();
        }

        return Response.ok( JsonUtil.toJson( trackerId ) ).build();
    }


    @Override
    public Response destroyEnvironment( final String environmentId )
    {
        try
        {
            environmentManager.cancelEnvironmentWorkflow( environmentId );

            environmentManager.destroyEnvironment( environmentId, false );
        }
        catch ( EnvironmentNotFoundException e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, "Environment is already destroyed" ) )
                           .build();
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response getSshKeys( final String environmentId )
    {
        try
        {
            Environment environment = environmentManager.loadEnvironment( environmentId );

            return Response.ok( JsonUtil.toJson( environment.getSshKeys() ) ).build();
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }
    }


    @Override
    public Response addSshKey( final String environmentId, final String key )
    {
        if ( Strings.isNullOrEmpty( environmentId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid environment id" ) ).build();
        }
        else if ( Strings.isNullOrEmpty( key ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid ssh key" ) ).build();
        }


        try
        {
            byte[] bytesEncoded = Base64.decodeBase64( key.getBytes() );
            environmentManager.addSshKey( environmentId, new String( bytesEncoded ).trim(), false );
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    /** Environments SSH keys **************************************************** */

    @Override
    public Response removeSshKey( final String environmentId, final String key )
    {

        if ( Strings.isNullOrEmpty( environmentId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid environment id" ) ).build();
        }
        else if ( Strings.isNullOrEmpty( key ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid ssh key" ) ).build();
        }

        try
        {
            byte[] bytesEncoded = Base64.decodeBase64( key.getBytes() );
            environmentManager.removeSshKey( environmentId, new String( bytesEncoded ).trim(), false );
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response getEnvironmentDomain( final String environmentId )
    {
        try
        {
            return Response.ok( JsonUtil.toJson( environmentManager.getEnvironmentDomain( environmentId ) ) ).build();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( e.getMessage() ) ).build();
        }
    }


    /** Environment domains **************************************************** */

    @Override
    public Response listDomainLoadBalanceStrategies()
    {
        return Response.ok( JsonUtil.toJson( ProxyLoadBalanceStrategy.values() ) ).build();
    }


    @Override
    public Response addEnvironmentDomain( String environmentId, String hostName, String strategyJson, Attachment attr )
    {
        try
        {
            String path = null;

            ProxyLoadBalanceStrategy strategy = JsonUtil.fromJson( strategyJson, ProxyLoadBalanceStrategy.class );

            try
            {
                // will throw exception if no attachment
                attr.getDataHandler().getContent();

                String certPath = System.getProperty( "java.io.tmpdir" ) + "/" + environmentId;

                File file = new File( certPath );

                if ( !file.createNewFile() )
                {
                    LOG.warn( "Domain ssl cert exists, overwriting..." );
                }

                attr.transferTo( file );

                // prefix path to get full container path from RH
                path = "/mnt/lib/lxc/management/" + certPath;
            }
            catch ( Exception e )
            {
                // path
            }

            environmentManager.assignEnvironmentDomain( environmentId, hostName, strategy, path );
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response removeEnvironmentDomain( String environmentId )
    {
        try
        {
            environmentManager.removeEnvironmentDomain( environmentId );
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response getContainerDomainNPort( final String environmentId, final String containerId )
    {
        try
        {
            if ( environmentManager.getEnvironmentDomain( environmentId ) == null )
            {
                return Response.serverError()
                               .entity( JsonUtil.toJson( "You must first register domain for environment" ) ).build();
            }

            Map<String, String> result = new HashMap<>();

            Environment environment = environmentManager.loadEnvironment( environmentId );

            EnvironmentContainerHost containerHost = environment.getContainerHostById( containerId );

            result.put( "status",
                    String.valueOf( environmentManager.isContainerInEnvironmentDomain( containerId, environmentId ) ) );

            result.put( "port", String.valueOf( containerHost.getDomainPort() ) );

            return Response.ok( JsonUtil.toJson( result ) ).build();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( e.getMessage() ) ).build();
        }
    }


    @Override
    public Response setContainerDomainNPort( final String environmentId, final String containerId, final Boolean state,
                                             final int port )
    {
        try
        {
            if ( !state )
            {
                environmentManager.removeContainerFromEnvironmentDomain( containerId, environmentId );
            }
            else
            {
                environmentManager.addContainerToEnvironmentDomain( containerId, environmentId, port );
            }
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( e.getMessage() ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response setContainerName( String environmentId, String containerId, String name )
    {
        try
        {
            Environment environment = findEnvironmentByContainerId( containerId );

            Preconditions.checkNotNull( environment, "Environment not found" );

            ContainerHost containerHost = environment.getContainerHostById( containerId );

            environmentManager.changeContainerHostname( containerHost.getContainerId(), name, false );
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( e.getMessage() ).build();
        }

        return Response.ok().build();
    }


    /** Containers **************************************************** */

    @Override
    public Response destroyContainer( final String containerId )
    {
        if ( Strings.isNullOrEmpty( containerId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid container id" ) ).build();
        }

        Environment environment = findEnvironmentByContainerId( containerId );

        if ( environment != null )
        {
            try
            {
                ContainerHost containerHost = environment.getContainerHostById( containerId );

                environmentManager.destroyContainer( environment.getId(), containerHost.getId(), false );

                return Response.ok().build();
            }
            catch ( ContainerHostNotFoundException | EnvironmentNotFoundException | EnvironmentModificationException e )
            {
                LOG.error( "Error destroying container #destroyContainer", e );
                return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
            }
        }

        return Response.status( Response.Status.NOT_FOUND ).build();
    }


    @Override
    public Response getContainerState( final String containerId )
    {
        if ( Strings.isNullOrEmpty( containerId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid container id" ) ).build();
        }


        Environment environment = findEnvironmentByContainerId( containerId );

        if ( environment != null )
        {
            try
            {
                ContainerHost containerHost = environment.getContainerHostById( containerId );

                return Response.ok().entity( JsonUtil.toJson( "STATE", containerHost.getState() ) ).build();
            }
            catch ( ContainerHostNotFoundException e )
            {
                LOG.error( "Error getting container state", e );
                return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
            }
        }

        return Response.status( Response.Status.NOT_FOUND ).build();
    }


    @Override
    public Response startContainer( final String containerId )
    {
        if ( Strings.isNullOrEmpty( containerId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid container id" ) ).build();
        }


        Environment environment = findEnvironmentByContainerId( containerId );

        if ( environment != null )
        {
            try
            {
                ContainerHost containerHost = environment.getContainerHostById( containerId );

                containerHost.start();

                return Response.ok().build();
            }
            catch ( PeerException e )
            {
                LOG.error( "Exception starting container host", e );
                return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
            }
        }

        return Response.status( Response.Status.NOT_FOUND ).build();
    }


    @Override
    public Response stopContainer( final String containerId )
    {
        if ( Strings.isNullOrEmpty( containerId ) )
        {
            return Response.status( Response.Status.BAD_REQUEST )
                           .entity( JsonUtil.toJson( ERROR_KEY, "Invalid container id" ) ).build();
        }


        Environment environment = findEnvironmentByContainerId( containerId );

        if ( environment != null )
        {
            try
            {
                ContainerHost containerHost = environment.getContainerHostById( containerId );

                containerHost.stop();

                return Response.ok().build();
            }
            catch ( PeerException e )
            {
                LOG.error( "Exception stopping container host", e );
                return Response.serverError().entity( JsonUtil.toJson( ERROR_KEY, e.getMessage() ) ).build();
            }
        }

        return Response.status( Response.Status.NOT_FOUND ).build();
    }


    /** Container types **************************************************** */
    @Override
    public Response listContainerTypes()
    {
        return Response.ok().entity( gson.toJson( ContainerSize.values() ) ).build();
    }


    @Override
    public Response listContainerTypesInfo()
    {
        try
        {
            return Response.ok().entity( gson.toJson( ContainerSize.getContainerSizeDescription() ) ).build();
        }
        catch ( Exception e )
        {
            return Response.serverError().entity( gson.toJson( e.getMessage() ) ).build();
        }
    }


    /** Peers strategy **************************************************** */
    @Override
    public Response listPlacementStrategies()
    {
        return Response.ok( JsonUtil.toJson( strategyManager.getPlacementStrategyTitles() ) ).build();
    }


    private CompletionService<Boolean> getCompletionService( Executor executor )
    {
        return new ExecutorCompletionService<>( executor );
    }


    /** Peers **************************************************** */
    @Override
    public Response getPeers()
    {
        List<Peer> peers = peerManager.getPeers();

        ExecutorService taskExecutor =
                Executors.newFixedThreadPool( Math.min( Common.MAX_EXECUTOR_SIZE, peers.size() ) );

        CompletionService<Boolean> taskCompletionService = getCompletionService( taskExecutor );

        Map<String, PeerDto> peerHostMap = Maps.newHashMap();

        try
        {
            for ( Peer peer : peers )
            {
                taskCompletionService.submit( () ->
                {
                    PeerDto peerDto = new PeerDto( peer.getId(), peer.getName(), peer.isOnline(), peer.isLocal() );
                    if ( peer.isOnline() )
                    {
                        Collection<ResourceHostMetric> collection = peer.getResourceHostMetrics().getResources();
                        for ( ResourceHostMetric metric : collection
                                .toArray( new ResourceHostMetric[collection.size()] ) )
                        {
                            peerDto.addResourceHostDto(
                                    new ResourceHostDto( metric.getHostInfo().getId(), metric.getHostName(),
                                            metric.getCpuModel(), metric.getUsedCpu().toString(),
                                            metric.getTotalRam().toString(), metric.getAvailableRam().toString(),
                                            metric.getTotalSpace().toString(), metric.getAvailableSpace().toString(),
                                            metric.isManagement() ) );
                        }
                    }

                    peerHostMap.put( peer.getId(), peerDto );
                    return true;
                } );
            }

            taskExecutor.shutdown();

            for ( int i = 0; i < peers.size(); i++ )
            {
                try
                {
                    Future<Boolean> future = taskCompletionService.take();
                    future.get();
                }
                catch ( ExecutionException | InterruptedException e )
                {
                    //ignored
                }
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Resource hosts are empty", e );
        }


        return Response.ok().entity( JsonUtil.toJson( peerHostMap ) ).build();
    }


    @Override
    public Response getResourceHosts()
    {
        List<ResourceHostDto> resourceHostDtos = Lists.newArrayList();
        try
        {
            LocalPeer localPeer = peerManager.getLocalPeer();

            for ( ResourceHost resourceHost : localPeer.getResourceHosts() )
            {
                resourceHostDtos.add( new ResourceHostDto( resourceHost.getId(), resourceHost.getHostname(),
                        resourceHost.getInstanceType(), resourceHost.isManagementHost(), resourceHost.getArch() ) );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Resource hosts are empty", e );
        }

        return Response.ok().entity( JsonUtil.toJson( resourceHostDtos ) ).build();
    }


    /** Tags **************************************************** */

    @Override
    public Response addTags( final String environmentId, final String containerId, final String tagsJson )
    {
        try
        {
            Environment environment = environmentManager.loadEnvironment( environmentId );

            EnvironmentContainerHost containerHost = environment.getContainerHostById( containerId );

            Set<String> tags = JsonUtil.fromJson( tagsJson, new TypeToken<Set<String>>()
            {
            }.getType() );

            for ( String tag : tags )
            {
                containerHost = containerHost.addTag( tag );
            }
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( e ) ).build();
        }

        return Response.ok().build();
    }


    @Override
    public Response removeTag( final String environmentId, final String containerId, final String tag )
    {
        try
        {
            Environment environment = environmentManager.loadEnvironment( environmentId );
            environment.getContainerHostById( containerId ).removeTag( tag );
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( JsonUtil.toJson( e ) ).build();
        }

        return Response.ok().build();
    }


    /** Additional *************************************** */


    @Override
    public Response setupContainerSsh( final String environmentId, final String containerId )
    {
        try
        {

            return Response.ok( JsonUtil
                    .toJson( environmentManager.setupSshTunnelForContainer( containerId, environmentId ) ) ).build();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.BAD_REQUEST ).entity( e ).build();
        }
    }


    @Override
    public Response getSharedUsers( final String objectId )
    {
        try
        {
            List<ShareDto> sharedUsers = secureEnvironmentManager.getSharedUsers( objectId );
            return Response.ok( JsonUtil.toJson( sharedUsers ) ).build();
        }
        catch ( Exception e )
        {
            return Response.status( Response.Status.INTERNAL_SERVER_ERROR ).entity( e.getMessage() ).build();
        }
    }


    @Override
    public Response share( final String users, final String environmentId )
    {
        ShareDto[] shareDto = gson.fromJson( users, ShareDto[].class );

        secureEnvironmentManager.shareEnvironment( shareDto, environmentId );

        return Response.ok().build();
    }


    private Environment findEnvironmentByContainerId( String containerId )
    {
        for ( Environment environment : environmentManager.getEnvironments() )
        {
            for ( ContainerHost containerHost : environment.getContainerHosts() )
            {
                if ( containerHost.getId().equals( containerId ) )
                {
                    return environment;
                }
            }
        }

        return null;
    }


    @Override
    public Response getDownloadProgress( String environmentId )
    {
        try
        {
            List<PeerTemplatesDownloadProgress> result =
                    environmentManager.loadEnvironment( environmentId ).getPeers().stream().map( p ->
                    {
                        try
                        {
                            return p.getTemplateDownloadProgress( new EnvironmentId( environmentId ) );
                        }
                        catch ( Exception e )
                        {
                            return new PeerTemplatesDownloadProgress( "NONE" );
                        }
                    } ).sorted( Comparator.comparing( PeerTemplatesDownloadProgress::getPeerId ) )
                                      .collect( Collectors.toList() );

            if ( result.stream().filter( s -> !s.getTemplatesDownloadProgresses().isEmpty() ).count() == 0 )
            {
                return Response.ok().build();
            }

            return Response.ok( JsonUtil.toJson( result ) ).build();
        }

        catch ( Exception e )
        {
            return Response.serverError().entity( e.getMessage() ).build();
        }
    }


    @Override
    public Response listEnvironments()
    {
        Set<Environment> environments = environmentManager.getEnvironments();
        Set<EnvironmentDto> environmentDtos = Sets.newHashSet();


        for ( Environment environment : environments )
        {
            try
            {
                String dataSource = ( environment instanceof HubEnvironment ) ? Common.HUB_ID : Common.SUBUTAI_ID;

                EnvironmentDto environmentDto =
                        new EnvironmentDto( environment.getId(), environment.getName(), environment.getStatus(),
                                convertContainersToContainerJson( environment.getContainerHosts(), dataSource ),
                                dataSource, environmentManager.getEnvironmentOwnerName( environment ) );

                environmentDtos.add( environmentDto );
            }
            catch ( Exception e )
            {
                LOG.error( "Error JSON-ifying environment {}: {}", environment.getId(), e.getMessage() );
            }
        }

        return Response.ok( JsonUtil.toJson( removeXss( environmentDtos ) ) ).build();
    }


    @Override
    public Response listTenantEnvironments()
    {
        Set<EnvironmentDto> tenantEnvs = environmentManager.getTenantEnvironments();

        return Response.ok( JsonUtil.toJson( removeXss( tenantEnvs ) ) ).build();
    }


    private Set<EnvironmentDto> removeXss( final Set<EnvironmentDto> environmentDtos )
    {
        for ( EnvironmentDto environmentDto : environmentDtos )
        {
            environmentDto.setName( StringUtil.removeHtml( environmentDto.getName() ) );

            for ( ContainerDto containerDto : environmentDto.getContainers() )
            {
                containerDto.setContainerName( StringUtil.removeHtml( containerDto.getContainerName() ) );
                containerDto.setHostname( StringUtil.removeHtml( containerDto.getHostname() ) );
                containerDto.setTemplateName( StringUtil.removeHtml( containerDto.getTemplateName() ) );
            }
        }

        return environmentDtos;
    }


    /** AUX **************************************************** */

    private Set<ContainerDto> convertContainersToContainerJson( Set<EnvironmentContainerHost> containerHosts,
                                                                String datasource )
    {
        Set<ContainerDto> containerDtos = Sets.newHashSet();

        for ( EnvironmentContainerHost containerHost : containerHosts )
        {
            containerDtos.add( new ContainerDto( containerHost.getId(), containerHost.getEnvironmentId().getId(),
                    containerHost.getHostname(), containerHost.getIp(), containerHost.getTemplateName(),
                    containerHost.getContainerSize(), containerHost.getArch().toString(), containerHost.getTags(),
                    containerHost.getPeerId(), containerHost.getResourceHostId().getId(), containerHost.isLocal(),
                    datasource, containerHost.getState(), containerHost.getTemplateId(),
                    containerHost.getContainerName(), containerHost.getResourceHostId().getId() ) );
        }

        return containerDtos;
    }


    private void checkName( final String name ) throws EnvironmentCreationException
    {
        if ( environmentManager.getEnvironments().stream().filter( e -> e.getName().equalsIgnoreCase( name.trim() ) )
                               .count() > 0 )
        {
            throw new EnvironmentCreationException( "Duplicated environment name" );
        }

        if ( name.trim().length() > 50 )
        {
            throw new EnvironmentCreationException( "Environment name is too long, it should be 50 chars max" );
        }
    }
}