package io.subutai.core.executor.impl;


import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;
import javax.ws.rs.core.Form;

import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import io.subutai.common.cache.ExpiringCache;
import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.Request;
import io.subutai.common.command.Response;
import io.subutai.common.host.ContainerHostInfo;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.ResourceHostInfo;
import io.subutai.common.settings.Common;
import io.subutai.common.settings.SystemSettings;
import io.subutai.common.util.JsonUtil;
import io.subutai.common.util.RestUtil;
import io.subutai.common.util.ServiceLocator;
import io.subutai.core.executor.api.RestProcessor;
import io.subutai.core.hostregistry.api.HostDisconnectedException;
import io.subutai.core.hostregistry.api.HostRegistry;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.identity.api.model.Session;
import io.subutai.core.security.api.SecurityManager;


/**
 * Executes commands and processes responses
 */
public class CommandProcessor implements RestProcessor
{
    private static final Logger LOG = LoggerFactory.getLogger( CommandProcessor.class.getName() );
    private static final int NOTIFIER_INTERVAL_MS = 300;
    private static final long COMMAND_ENTRY_TIMEOUT =
            ( Common.INACTIVE_COMMAND_DROP_TIMEOUT_SEC + Common.DEFAULT_AGENT_RESPONSE_CHUNK_INTERVAL ) * 1000
                    + NOTIFIER_INTERVAL_MS + 1000;
    private final HostRegistry hostRegistry;
    private IdentityManager identityManager;
    protected ExpiringCache<UUID, CommandProcess> commands = new ExpiringCache<>();
    protected final ExpiringCache<String, Set<String>> requests = new ExpiringCache<>();
    protected ScheduledExecutorService notifier = Executors.newSingleThreadScheduledExecutor();
    protected ExecutorService notifierPool = Executors.newCachedThreadPool();


    public CommandProcessor( final HostRegistry hostRegistry, final IdentityManager identityManager )
    {
        Preconditions.checkNotNull( hostRegistry );
        Preconditions.checkNotNull( identityManager );

        this.hostRegistry = hostRegistry;
        this.identityManager = identityManager;

        notifier.scheduleWithFixedDelay( new Runnable()
        {
            public void run()
            {
                try
                {
                    notifyAgents();
                }
                catch ( Exception e )
                {
                    LOG.error( "Error in notifier task", e );
                }
            }
        }, 0, NOTIFIER_INTERVAL_MS, TimeUnit.MILLISECONDS );
    }


    public void execute( final Request request, CommandCallback callback ) throws CommandException
    {

        //find target host
        ResourceHostInfo resourceHostInfo;
        try
        {
            resourceHostInfo = getResourceHostInfo( request.getId() );
        }
        catch ( HostDisconnectedException e )
        {
            throw new CommandException( e );
        }

        //create command process
        CommandProcess commandProcess = new CommandProcess( this, callback, request, getActiveSession() );
        boolean queued = commands.put( request.getCommandId(), commandProcess, COMMAND_ENTRY_TIMEOUT,
                new CommandProcessExpiryCallback() );
        if ( !queued )
        {
            throw new CommandException( "This command is already queued for execution" );
        }

        //send command
        try
        {
            commandProcess.start();

            String command = JsonUtil.toJson( new RequestWrapper( request ) );

            LOG.info( String.format( "Sending:%n%s", command ) );

            //queue request
            queueRequest( resourceHostInfo, request );
        }
        catch ( Exception e )
        {
            LOG.error( "Error sending request", e );

            remove( request );

            commandProcess.stop();

            throw new CommandException( e );
        }
    }


    protected void queueRequest( ResourceHostInfo resourceHostInfo, Request request )
            throws NamingException, PGPException
    {
        //add request to outgoing agent queue
        synchronized ( requests )
        {
            Set<String> hostRequests = requests.get( resourceHostInfo.getId() );
            if ( hostRequests == null )
            {
                hostRequests = Sets.newLinkedHashSet();
                requests.put( resourceHostInfo.getId(), hostRequests, Common.INACTIVE_COMMAND_DROP_TIMEOUT_SEC * 1000 );
            }
            String encryptedRequest = encrypt( JsonUtil.toJson( request ), request.getId() );
            hostRequests.add( encryptedRequest );
        }
    }


    @Override
    public Set<String> getRequests( final String hostId )
    {
        Set<String> hostRequests;
        synchronized ( requests )
        {
            hostRequests = requests.remove( hostId );
        }
        return hostRequests == null ? Sets.<String>newHashSet() : hostRequests;
    }


    protected void notifyAgents()
    {
        Set<String> resourceHostIds = requests.getEntries().keySet();

        for ( final String resourceHostId : resourceHostIds )
        {
            notifierPool.execute( new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        ResourceHostInfo resourceHostInfo = getResourceHostInfo( resourceHostId );

                        notifyAgent( resourceHostInfo );
                    }
                    catch ( Exception e )
                    {
                        //ignore
                    }
                }
            } );
        }
    }


    protected String encrypt( String message, String hostId ) throws NamingException, PGPException
    {
        return getSecurityManager().signNEncryptRequestToHost( message, hostId );
    }


    protected void notifyAgent( ResourceHostInfo resourceHostInfo )
    {
        WebClient webClient = null;
        try
        {
            webClient = getWebClient( resourceHostInfo );

            javax.ws.rs.core.Response response = webClient.form( new Form() );

            if ( response.getStatus() == javax.ws.rs.core.Response.Status.OK.getStatusCode()
                    || response.getStatus() == javax.ws.rs.core.Response.Status.ACCEPTED.getStatusCode() )
            {
                hostRegistry.updateResourceHostEntryTimestamp( resourceHostInfo.getId() );
            }
        }
        finally
        {
            RestUtil.closeClient( webClient );
        }
    }


    protected WebClient getWebClient( ResourceHostInfo resourceHostInfo )
    {
        return RestUtil.createWebClient( String.format( "http://%s:%d/trigger", getResourceHostIp( resourceHostInfo ),
                SystemSettings.getAgentPort() ), 1000, 1000, 3 );
    }


    protected String getResourceHostIp( ResourceHostInfo resourceHostInfo )
    {
        return resourceHostInfo.getHostInterfaces().findByName( SystemSettings.getExternalIpInterface() ).getIp();
    }


    protected ResourceHostInfo getResourceHostInfo( String requestHostId ) throws HostDisconnectedException
    {
        try
        {
            return hostRegistry.getResourceHostInfoById( requestHostId );
        }
        catch ( HostDisconnectedException e )
        {
            ContainerHostInfo containerHostInfo = hostRegistry.getContainerHostInfoById( requestHostId );

            return hostRegistry.getResourceHostByContainerHost( containerHostInfo );
        }
    }


    protected SecurityManager getSecurityManager() throws NamingException
    {
        return ServiceLocator.getServiceNoCache( SecurityManager.class );
    }


    protected Session getActiveSession()
    {
        return identityManager.getActiveSession();
    }


    public CommandResult getResult( UUID commandId ) throws CommandException
    {
        Preconditions.checkNotNull( commandId );

        CommandProcess commandProcess = commands.get( commandId );
        if ( commandProcess != null )
        {
            //wait until process completes  & return result
            return commandProcess.waitResult();
        }
        else
        {
            throw new CommandException( String.format( "Command process not found by id: %s", commandId ) );
        }
    }


    @Override
    public void handleResponse( final Response response )
    {
        try
        {
            Preconditions.checkNotNull( response );

            CommandProcess commandProcess = commands.get( response.getCommandId() );

            if ( commandProcess != null )
            {

                LOG.info( String.format( "Received:%n%s", JsonUtil.toJson( response ) ) );

                //process response
                commandProcess.processResponse( response );
            }
            else
            {
                LOG.warn( String.format( "Callback not found for response: %s", JsonUtil.toJson( response ) ) );
            }

            //update rh timestamp
            ResourceHostInfo resourceHostInfo = getResourceHostInfo( response.getId() );

            hostRegistry.updateResourceHostEntryTimestamp( resourceHostInfo.getId() );
        }
        catch ( Exception e )
        {
            LOG.error( "Error processing response", e );
        }
    }


    protected ResourceHostInfo getTargetHost( String hostId ) throws HostDisconnectedException
    {
        ResourceHostInfo targetHost;

        try
        {
            targetHost = hostRegistry.getResourceHostInfoById( hostId );
        }
        catch ( HostDisconnectedException e )
        {
            ContainerHostInfo containerHostInfo = hostRegistry.getContainerHostInfoById( hostId );
            if ( containerHostInfo.getState() != ContainerHostState.RUNNING )
            {
                throw new HostDisconnectedException(
                        String.format( "Container state is %s", containerHostInfo.getState() ) );
            }
            targetHost = hostRegistry.getResourceHostByContainerHost( containerHostInfo );
        }

        return targetHost;
    }


    protected void remove( Request request )
    {
        Preconditions.checkNotNull( request );

        commands.remove( request.getCommandId() );
    }


    public void dispose()
    {
        commands.dispose();

        requests.dispose();

        notifier.shutdown();

        notifierPool.shutdown();
    }
}
