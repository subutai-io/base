package io.subutai.core.hubmanager.impl;


import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import io.subutai.common.dao.DaoManager;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.security.utils.SafeCloseUtil;
import io.subutai.common.util.CollectionUtil;
import io.subutai.common.util.RestUtil;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.executor.api.CommandExecutor;
import io.subutai.core.hubmanager.api.HubManager;
import io.subutai.core.hubmanager.api.StateLinkProcessor;
import io.subutai.core.hubmanager.api.dao.ConfigDataService;
import io.subutai.core.hubmanager.api.exception.HubManagerException;
import io.subutai.core.hubmanager.api.model.Config;
import io.subutai.core.hubmanager.impl.appscale.AppScaleManager;
import io.subutai.core.hubmanager.impl.appscale.AppScaleProcessor;
import io.subutai.core.hubmanager.impl.dao.ConfigDataServiceImpl;
import io.subutai.core.hubmanager.impl.environment.HubEnvironmentProcessor;
import io.subutai.core.hubmanager.impl.environment.state.Context;
import io.subutai.core.hubmanager.impl.http.HubRestClient;
import io.subutai.core.hubmanager.impl.processor.CommandProcessor;
import io.subutai.core.hubmanager.impl.processor.ContainerEventProcessor;
import io.subutai.core.hubmanager.impl.processor.EnvironmentUserHelper;
import io.subutai.core.hubmanager.impl.processor.HeartbeatProcessor;
import io.subutai.core.hubmanager.impl.processor.HubLoggerProcessor;
import io.subutai.core.hubmanager.impl.processor.ProductProcessor;
import io.subutai.core.hubmanager.impl.processor.RegistrationRequestProcessor;
import io.subutai.core.hubmanager.impl.processor.ResourceHostDataProcessor;
import io.subutai.core.hubmanager.impl.processor.ResourceHostMonitorProcessor;
import io.subutai.core.hubmanager.impl.processor.ResourceHostRegisterProcessor;
import io.subutai.core.hubmanager.impl.processor.SystemConfProcessor;
import io.subutai.core.hubmanager.impl.processor.VehsProcessor;
import io.subutai.core.hubmanager.impl.processor.VersionInfoProcessor;
import io.subutai.core.hubmanager.impl.tunnel.TunnelEventProcessor;
import io.subutai.core.hubmanager.impl.tunnel.TunnelProcessor;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.identity.api.model.User;
import io.subutai.core.metric.api.Monitor;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.core.registration.api.HostRegistrationManager;
import io.subutai.core.security.api.SecurityManager;
import io.subutai.hub.share.common.HubEventListener;
import io.subutai.hub.share.dto.PeerDto;
import io.subutai.hub.share.dto.PeerProductDataDto;
import io.subutai.hub.share.dto.SystemConfDto;
import io.subutai.hub.share.dto.UserDto;
import io.subutai.hub.share.dto.product.ProductsDto;
import io.subutai.hub.share.json.JsonUtil;


// TODO: Replace WebClient with HubRestClient.
public class HubManagerImpl implements HubManager
{
    private static final long TIME_15_MINUTES = 900;

    private final Logger log = LoggerFactory.getLogger( getClass() );

    private final ScheduledExecutorService heartbeatExecutorService = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService resourceHostConfExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService resourceHostMonitorExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService hubLoggerExecutorService = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService containerEventExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService environmentTelemetryService = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService versionEventExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService tunnelEventService = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService sumChecker = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService registrationRequestExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService asyncHeartbeatExecutor = Executors.newFixedThreadPool( 3 );

    private SecurityManager securityManager;

    private EnvironmentManager envManager;

    private PeerManager peerManager;

    private ConfigManager configManager;

    private CommandExecutor commandExecutor;

    private DaoManager daoManager;

    private ConfigDataService configDataService;

    private Monitor monitor;

    private IdentityManager identityManager;

    private HostRegistrationManager hostRegistrationManager;

    private HeartbeatProcessor heartbeatProcessor;

    //todo inject this as dependency
    private ResourceHostDataProcessor resourceHostDataProcessor;

    private ContainerEventProcessor containerEventProcessor;

    private VersionInfoProcessor versionInfoProcessor;

    private RegistrationRequestProcessor registrationRequestProcessor;

    private final Set<HubEventListener> hubEventListeners = Sets.newConcurrentHashSet();

    private String checksum = "";

    private HubRestClient restClient;

    private LocalPeer localPeer;

    private EnvironmentUserHelper envUserHelper;


    public HubManagerImpl( DaoManager daoManager )
    {
        this.daoManager = daoManager;
    }


    public void addListener( HubEventListener listener )
    {
        if ( listener != null )
        {
            hubEventListeners.add( listener );
        }
    }


    public void removeListener( HubEventListener listener )
    {
        if ( listener != null )
        {
            hubEventListeners.remove( listener );
        }
    }


    public void init()
    {
        try
        {
            localPeer = peerManager.getLocalPeer();

            configDataService = new ConfigDataServiceImpl( daoManager );

            configManager = new ConfigManager( securityManager, peerManager, identityManager );

            restClient = new HubRestClient( configManager );

            resourceHostDataProcessor = new ResourceHostDataProcessor( this, localPeer, monitor, restClient );

            ResourceHostMonitorProcessor resourceHostMonitorProcessor =
                    new ResourceHostMonitorProcessor( this, peerManager, configManager, monitor );

            resourceHostConfExecutorService
                    .scheduleWithFixedDelay( resourceHostDataProcessor, 20, TIME_15_MINUTES, TimeUnit.SECONDS );

            resourceHostMonitorExecutorService
                    .scheduleWithFixedDelay( resourceHostMonitorProcessor, 30, 300, TimeUnit.SECONDS );

            containerEventProcessor = new ContainerEventProcessor( this, configManager, peerManager );

            containerEventExecutor.scheduleWithFixedDelay( containerEventProcessor, 30, 300, TimeUnit.SECONDS );

            //todo inject this as dependency
            HubLoggerProcessor hubLoggerProcessor = new HubLoggerProcessor( configManager, this );

            hubLoggerExecutorService.scheduleWithFixedDelay( hubLoggerProcessor, 40, 3600, TimeUnit.SECONDS );

            TunnelEventProcessor tunnelEventProcessor = new TunnelEventProcessor( this, peerManager, configManager );

            tunnelEventService.scheduleWithFixedDelay( tunnelEventProcessor, 20, 300, TimeUnit.SECONDS );

            versionInfoProcessor = new VersionInfoProcessor( this, peerManager, configManager );

            versionEventExecutor.scheduleWithFixedDelay( versionInfoProcessor, 20, 120, TimeUnit.SECONDS );

            registrationRequestProcessor =
                    new RegistrationRequestProcessor( this, peerManager, hostRegistrationManager, restClient );

            registrationRequestExecutor
                    .scheduleWithFixedDelay( registrationRequestProcessor, 20, 60, TimeUnit.SECONDS );

            EnvironmentTelemetryProcessor environmentTelemetryProcessor =
                    new EnvironmentTelemetryProcessor( this, peerManager, configManager );

            environmentTelemetryService
                    .scheduleWithFixedDelay( environmentTelemetryProcessor, 20, 1800, TimeUnit.SECONDS );

            this.sumChecker.scheduleWithFixedDelay( new Runnable()
            {
                @Override
                public void run()
                {
                    log.info( "Starting sumchecker" );
                    generateChecksum();
                }
            }, 1, 600000, TimeUnit.MILLISECONDS );


            envUserHelper = new EnvironmentUserHelper( identityManager, configDataService, envManager, restClient );

            initHeartbeatProcessor();
        }
        catch ( Exception e )
        {
            log.error( e.getMessage() );
        }
    }


    @Override
    public boolean isHubReachable()
    {
        if ( heartbeatProcessor != null )
        {
            return heartbeatProcessor.isHubReachable();
        }

        return false;
    }


    private void initHeartbeatProcessor()
    {
        StateLinkProcessor tunnelProcessor = new TunnelProcessor( peerManager, configManager );

        Context ctx = new Context( identityManager, envManager, envUserHelper, localPeer, restClient );

        StateLinkProcessor hubEnvironmentProcessor = new HubEnvironmentProcessor( ctx );

        StateLinkProcessor systemConfProcessor = new SystemConfProcessor( configManager );

        ProductProcessor productProcessor = new ProductProcessor( configManager, this.hubEventListeners );

        StateLinkProcessor vehsProccessor = new VehsProcessor( configManager, peerManager );

        AppScaleProcessor appScaleProcessor =
                new AppScaleProcessor( configManager, new AppScaleManager( peerManager ) );

        EnvironmentTelemetryProcessor environmentTelemetryProcessor =
                new EnvironmentTelemetryProcessor( this, peerManager, configManager );

        StateLinkProcessor resourceHostRegisterProcessor =
                new ResourceHostRegisterProcessor( hostRegistrationManager, peerManager, restClient );

        StateLinkProcessor commandProcessor = new CommandProcessor( ctx );


        heartbeatProcessor =
                new HeartbeatProcessor( this, restClient, localPeer.getId() ).addProcessor( tunnelProcessor )
                                                                             .addProcessor( hubEnvironmentProcessor )
                                                                             .addProcessor( systemConfProcessor )
                                                                             .addProcessor( productProcessor )
                                                                             .addProcessor( vehsProccessor )
                                                                             .addProcessor( appScaleProcessor )
                                                                             .addProcessor(
                                                                                     environmentTelemetryProcessor )
                                                                             .addProcessor(
                                                                                     resourceHostRegisterProcessor )
                                                                             .addProcessor( commandProcessor );

        heartbeatExecutorService
                .scheduleWithFixedDelay( heartbeatProcessor, 5, HeartbeatProcessor.SMALL_INTERVAL_SECONDS,
                        TimeUnit.SECONDS );
    }


    public void destroy()
    {
        heartbeatExecutorService.shutdown();
        resourceHostConfExecutorService.shutdown();
        resourceHostMonitorExecutorService.shutdown();
    }


    @Override
    public void sendHeartbeat() throws HubManagerException
    {
        resourceHostDataProcessor.process( false );
        heartbeatProcessor.sendHeartbeat( true );
        containerEventProcessor.process();
    }


    /**
     * Called by Hub to trigger heartbeat on peer
     */
    @Override
    public void triggerHeartbeat()
    {
        asyncHeartbeatExecutor.execute( new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    heartbeatProcessor.sendHeartbeat( true );
                }
                catch ( Exception e )
                {
                    log.error( "Error on triggering heartbeat: ", e );
                }
            }
        } );
    }


    @Override
    public void sendResourceHostInfo() throws HubManagerException
    {
        resourceHostDataProcessor.process( true );
    }


    @RolesAllowed( { "Peer-Management|Delete", "Peer-Management|Update" } )
    @Override
    public void registerPeer( String hupIp, String email, String password, String peerName ) throws HubManagerException
    {
        RegistrationManager registrationManager = new RegistrationManager( this, configManager, hupIp );

        registrationManager.registerPeer( email, password, peerName );

        generateChecksum();

        notifyRegistrationListeners();

        sendResourceHostInfo();

        registrationRequestProcessor.run();
    }


    private void notifyRegistrationListeners()
    {
        if ( !CollectionUtil.isCollectionEmpty( hubEventListeners ) )
        {
            ExecutorService notifier = Executors.newFixedThreadPool( hubEventListeners.size() );

            for ( final HubEventListener hubEventListener : hubEventListeners )
            {
                notifier.execute( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            hubEventListener.onRegistrationSucceeded();
                        }
                        catch ( Exception e )
                        {
                            log.error( "Error notifying hub event listener", e );
                        }
                    }
                } );
            }

            notifier.shutdown();
        }
    }


    @RolesAllowed( { "Peer-Management|Delete", "Peer-Management|Update" } )
    @Override
    public void unregisterPeer() throws HubManagerException
    {
        RegistrationManager registrationManager = new RegistrationManager( this, configManager, null );

        registrationManager.unregister();
    }


    @Override
    public String getHubDns() throws HubManagerException
    {
        Config config = getConfigDataService().getHubConfig( configManager.getPeerId() );

        if ( config != null )
        {
            return config.getHubIp();
        }
        else
        {
            return null;
        }
    }


    @Override
    public String getProducts() throws HubManagerException
    {
        try
        {
            WebClient client = configManager
                    .getTrustedWebClientWithAuth( "/rest/v1.2/marketplace/products/public", "hub.subut.ai" );

            Response r = client.get();

            if ( r.getStatus() == HttpStatus.SC_NO_CONTENT )
            {
                return null;
            }

            if ( r.getStatus() != HttpStatus.SC_OK )
            {
                log.error( r.readEntity( String.class ) );
                return null;
            }

            String result = r.readEntity( String.class );
            ProductsDto productsDto = new ProductsDto( result );
            return JsonUtil.toJson( productsDto );
        }
        catch ( Exception e )
        {
            throw new HubManagerException( "Could not retrieve product data", e );
        }
    }


    @Override
    public void installPlugin( String url, String name, String uid ) throws HubManagerException
    {
        InputStream initialStream = null;
        try
        {
            WebClient webClient = RestUtil.createTrustedWebClient( url );
            File product = webClient.get( File.class );
            initialStream = FileUtils.openInputStream( product );
            File targetFile =
                    new File( String.format( "%s/deploy", System.getProperty( "karaf.home" ) ) + "/" + name + ".kar" );
            FileUtils.copyInputStreamToFile( initialStream, targetFile );
            initialStream.close();

            if ( isRegistered() )
            {
                ProductProcessor productProcessor = new ProductProcessor( this.configManager, this.hubEventListeners );
                Set<String> links = new HashSet<>();
                links.add( productProcessor.getProductProcessUrl( uid ) );
                PeerProductDataDto peerProductDataDto = new PeerProductDataDto();
                peerProductDataDto.setProductId( uid );
                peerProductDataDto.setState( PeerProductDataDto.State.INSTALLED );
                peerProductDataDto.setInstallDate( new Date() );

                try
                {
                    productProcessor.updatePeerProductData( peerProductDataDto );
                }
                catch ( Exception e )
                {
                    log.error( "Failed to send plugin install command to Hub: {}", e.getMessage() );
                }
            }

            log.debug( "Product installed successfully..." );
        }
        catch ( Exception e )
        {
            throw new HubManagerException( e );
        }
        finally
        {
            SafeCloseUtil.close( initialStream );
        }
    }


    @Override
    public void uninstallPlugin( final String name, final String uid )
    {
        File file = new File( String.format( "%s/deploy", System.getProperty( "karaf.home" ) ) + "/" + name + ".kar" );

        if ( file.delete() )
        {
            log.debug( file.getName() + " is removed." );
        }

        if ( isRegistered() )
        {
            ProductProcessor productProcessor = new ProductProcessor( this.configManager, this.hubEventListeners );
            PeerProductDataDto peerProductDataDto = new PeerProductDataDto();
            peerProductDataDto.setProductId( uid );
            peerProductDataDto.setState( PeerProductDataDto.State.REMOVE );

            try
            {
                productProcessor.deletePeerProductData( peerProductDataDto );
            }
            catch ( Exception e )
            {
                log.error( "Failed to send plugin remove command to Hub: {}", e.getMessage() );
            }
        }

        log.debug( "Product uninstalled successfully..." );
    }


    @Override
    public boolean isRegistered()
    {
        return configDataService.getHubConfig( configManager.getPeerId() ) != null;
    }


    @Override
    public Map<String, String> getPeerInfo() throws HubManagerException
    {
        Map<String, String> result = new HashMap<>();
        try
        {
            String path = "/rest/v1/peers/" + configManager.getPeerId();

            WebClient client = configManager.getTrustedWebClientWithAuth( path, configManager.getHubIp() );

            Response r = client.get();

            if ( r.getStatus() == HttpStatus.SC_OK )
            {
                byte[] encryptedContent = configManager.readContent( r );
                byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
                PeerDto dto = JsonUtil.fromCbor( plainContent, PeerDto.class );
                result.put( "OwnerId", dto.getOwnerId() );

                log.debug( "PeerDto: " + result.toString() );
            }
        }
        catch ( Exception e )
        {
            throw new HubManagerException( "Could not retrieve Peer info", e );
        }
        return result;
    }


    public CommandExecutor getCommandExecutor()
    {
        return commandExecutor;
    }


    public void setCommandExecutor( final CommandExecutor commandExecutor )
    {
        this.commandExecutor = commandExecutor;
    }


    @Override
    public Config getHubConfiguration()
    {
        return configDataService.getHubConfig( configManager.getPeerId() );
    }


    public void setEnvironmentManager( final EnvironmentManager environmentManager )
    {
        this.envManager = environmentManager;
    }


    public void setSecurityManager( final SecurityManager securityManager )
    {
        this.securityManager = securityManager;
    }


    public void setPeerManager( final PeerManager peerManager )
    {
        this.peerManager = peerManager;
    }


    public ConfigDataService getConfigDataService()
    {
        return configDataService;
    }


    public void setConfigDataService( final ConfigDataService configDataService )
    {
        this.configDataService = configDataService;
    }


    public void setMonitor( Monitor monitor )
    {
        this.monitor = monitor;
    }


    private static ObjectMapper createMapper( JsonFactory factory )
    {
        ObjectMapper mapper = new ObjectMapper( factory );
        mapper.setVisibility( PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY );
        return mapper;
    }


    public void setIdentityManager( final IdentityManager identityManager )
    {
        this.identityManager = identityManager;
    }


    public void setHostRegistrationManager( final HostRegistrationManager hostRegistrationManager )
    {
        this.hostRegistrationManager = hostRegistrationManager;
    }


    private void generateChecksum()
    {
        try
        {
            log.info( "Generating plugins list md5 checksum" );
            String productList = getProducts();
            MessageDigest md = MessageDigest.getInstance( "MD5" );
            byte[] bytes = md.digest( productList.getBytes( "UTF-8" ) );
            StringBuilder hexString = new StringBuilder();

            for ( int i = 0; i < bytes.length; i++ )
            {
                String hex = Integer.toHexString( 0xFF & bytes[i] );
                if ( hex.length() == 1 )
                {
                    hexString.append( '0' );
                }
                hexString.append( hex );
            }

            checksum = hexString.toString();
            log.info( "Checksum generated: " + checksum );
        }
        catch ( Exception e )
        {
            log.error( e.getMessage() );
        }
    }


    @Override
    public String getChecksum()
    {
        return this.checksum;
    }


    @Override
    public void sendSystemConfiguration( final SystemConfDto dto )
    {
        if ( isRegistered() )
        {
            try
            {
                String path = "/rest/v1/system-changes";
                WebClient client = configManager.getTrustedWebClientWithAuth( path, configManager.getHubIp() );

                byte[] cborData = JsonUtil.toCbor( dto );

                byte[] encryptedData = configManager.getMessenger().produce( cborData );

                log.info( "Sending Configuration of SS to Hub..." );

                Response r = client.post( encryptedData );

                if ( r.getStatus() == HttpStatus.SC_NO_CONTENT )
                {
                    log.info( "SS configuration sent successfully." );
                }
                else
                {
                    log.error( "Could not send SS configuration to Hub: ", r.readEntity( String.class ) );
                }
            }
            catch ( Exception e )
            {
                log.error( "Could not send SS configuration to Hub", e );
            }
        }
    }


    @Override
    public String getCurrentUserEmail()
    {
        User currentUser = identityManager.getActiveUser();

        String email = currentUser.getEmail();

        log.info( "currentUser: id={}, username={}, email={}", currentUser.getId(), currentUser.getUserName(), email );

        if ( !email.contains( "@hub.subut.ai" ) )
        {
            return getHubConfiguration().getOwnerEmail();
        }

        UserDto userDto = envUserHelper.getUserDataFromHub( StringUtils.substringBefore( email, "@" ) );

        return userDto.getEmail();
    }
}