package io.subutai.core.metric.impl;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.time.DateUtils;

import com.google.common.base.Preconditions;
import com.google.gson.JsonSyntaxException;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.dao.DaoManager;
import io.subutai.common.exception.DaoException;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.HostInfoModel;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.host.ResourceHostInfo;
import io.subutai.common.host.ResourceHostInfoModel;
import io.subutai.common.metric.Alert;
import io.subutai.common.metric.ProcessResourceUsage;
import io.subutai.common.metric.QuotaAlert;
import io.subutai.common.metric.QuotaAlertValue;
import io.subutai.common.metric.ResourceHostMetric;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.peer.AlertEvent;
import io.subutai.common.peer.AlertListener;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.HostNotFoundException;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.resource.HistoricalMetrics;
import io.subutai.common.util.JsonUtil;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.hostregistry.api.HostDisconnectedException;
import io.subutai.core.hostregistry.api.HostListener;
import io.subutai.core.hostregistry.api.HostRegistry;
import io.subutai.core.metric.api.Monitor;
import io.subutai.core.metric.api.MonitorException;
import io.subutai.core.peer.api.PeerManager;


/**
 * Implementation of Monitor
 */
public class MonitorImpl implements Monitor, HostListener
{
    private static final Logger LOG = LoggerFactory.getLogger( MonitorImpl.class );
    private static final int ALERT_LIVE_TIME = 2;// alert live time in min
    private final HostRegistry hostRegistry;

    protected Set<AlertListener> alertListeners =
            Collections.newSetFromMap( new ConcurrentHashMap<AlertListener, Boolean>() );

    private final Commands commands = new Commands();
    protected MonitorDao monitorDao;
    protected DaoManager daoManager;

    protected ScheduledExecutorService backgroundTasksExecutorService;
    protected ExecutorService notificationExecutor = Executors.newCachedThreadPool();

    private Map<String, AlertEvent> alertQueue = new HashMap<>();

    private List<AlertEvent> alerts = new CopyOnWriteArrayList<>();

    private PeerManager peerManager;
    protected ObjectMapper mapper = new ObjectMapper();


    public MonitorImpl( PeerManager peerManager, DaoManager daoManager, EnvironmentManager environmentManager,
                        HostRegistry hostRegistry ) throws MonitorException
    {
        Preconditions.checkNotNull( peerManager );
        Preconditions.checkNotNull( daoManager );
        Preconditions.checkNotNull( hostRegistry );

        try
        {
            this.daoManager = daoManager;
            this.monitorDao = new MonitorDao( daoManager.getEntityManagerFactory() );
            this.peerManager = peerManager;
            this.hostRegistry = hostRegistry;
        }
        catch ( DaoException e )
        {
            throw new MonitorException( e );
        }

        backgroundTasksExecutorService = Executors.newScheduledThreadPool( 1 );
        backgroundTasksExecutorService.scheduleWithFixedDelay( new BackgroundTasksRunner(), 10, 30, TimeUnit.SECONDS );
    }


    public void addAlertListener( AlertListener alertListener )
    {
        if ( alertListener != null && alertListener.getId() != null && !"".equals( alertListener.getId().trim() ) )
        {
            alertListeners.add( alertListener );
        }
    }


    public void removeAlertListener( AlertListener alertListener )
    {
        alertListeners.remove( alertListener );
    }


    @Override
    public Set<AlertListener> getAlertListeners()
    {
        return alertListeners;
    }


    private ResourceHostMetric fetchResourceHostMetric( ResourceHost resourceHost )
    {
        ResourceHostMetric result = null;
        try
        {

            CommandResult commandResult =
                    resourceHost.execute( commands.getCurrentMetricCommand( resourceHost.getHostname() ) );
            if ( commandResult.hasSucceeded() )
            {
                result = JsonUtil.fromJson( commandResult.getStdOut(), ResourceHostMetric.class );
                LOG.debug( String.format( "Host %s metrics fetched successfully.", resourceHost.getHostname() ) );
            }
            else
            {
                LOG.warn( String.format( "Error getting %s metrics", resourceHost.getHostname() ) );
            }
        }
        catch ( CommandException | JsonSyntaxException e )
        {
            LOG.error( e.getMessage(), e );
        }

        return result;
    }


    @Override
    public ProcessResourceUsage getProcessResourceUsage( final ContainerId containerId, int pid )
            throws MonitorException
    {
        Preconditions.checkNotNull( containerId );
        Preconditions.checkArgument( pid > 0 );
        try
        {

            Host c = peerManager.getLocalPeer().bindHost( containerId );
            ResourceHost resourceHost = peerManager.getLocalPeer().getResourceHostByContainerName( c.getHostname() );

            CommandResult commandResult =
                    resourceHost.execute( commands.getProcessResourceUsageCommand( c.getHostname(), pid ) );
            if ( !commandResult.hasSucceeded() )
            {
                throw new MonitorException(
                        String.format( "Error getting process resource usage of pid=%d on %s: %s %s", pid,
                                c.getHostname(), commandResult.getStatus(), commandResult.getStdErr() ) );
            }

            ProcessResourceUsage result = JsonUtil.fromJson( commandResult.getStdOut(), ProcessResourceUsage.class );
            result.setContainerId( containerId );

            return result;
        }
        catch ( Exception e )
        {
            LOG.error( String.format( "Could not obtain process resource usage for container %s, pid %d",
                    containerId.getId(), pid ), e );
            throw new MonitorException( e );
        }
    }


    public void destroy()
    {
        backgroundTasksExecutorService.shutdown();
        notificationExecutor.shutdown();
    }


    @Override
    public HistoricalMetrics getHistoricalMetrics( final Host host, Date startTime, Date endTime )
    {
        Preconditions.checkNotNull( host );

        HistoricalMetrics result = new HistoricalMetrics();

        try
        {
            RequestBuilder historicalMetricCommand = commands.getHistoricalMetricCommand( host, startTime, endTime );

            CommandResult commandResult;
            if ( host instanceof ResourceHost )
            {
                commandResult = peerManager.getLocalPeer().getResourceHostById( host.getId() )
                                           .execute( historicalMetricCommand );
            }
            else
            {
                commandResult = peerManager.getLocalPeer().getManagementHost().execute( historicalMetricCommand );
            }


            if ( null != commandResult && commandResult.hasSucceeded() )
            {
                result = mapper.readValue( commandResult.getStdOut(), HistoricalMetrics.class );
            }
            else
            {
                LOG.error( String.format( "Error getting historical metrics from %s: %s", host.getHostname(),
                        commandResult.getStdErr() ) );
            }
        }
        catch ( IOException | CommandException e )
        {
            LOG.error( "Could not run command successfully! Error: {}", e );
        }
        catch ( HostNotFoundException e )
        {
            LOG.error( "Could not find resource host of host {}!", host.getHostname() );
        }


        return result;
    }


    @Override
    public void addAlert( final AlertEvent alert )
    {
        AlertEvent a = new AlertEvent( alert.getPeerId(), alert.getEnvironmentId(), alert.getContainerId(),
                alert.getTemplateName(), alert.getResource(), alert.getExpiredTime() );
        LOG.debug( "Accepted new alert: " + a );
        alerts.add( a );
    }


    protected void queueAlertResource( Alert alert )
    {
        if ( alert == null || !alert.validate() )
        {
            // skipping invalid alert
            return;
        }

        AlertEvent alertEvent = alertQueue.get( alert.getId() );
        if ( alertEvent != null )
        {
            if ( !alertEvent.isExpired() )
            {
                // skipping, alert already exists
                LOG.debug( String.format( "Alert already in queue. %s", alertEvent ) );
                return;
            }
        }

        alertEvent = buildAlertPack( alert );
        alertQueue.put( alert.getId(), alertEvent );
    }


    private AlertEvent buildAlertPack( Alert alert )
    {
        String containerId = alert.getHostId().getId();
        AlertEvent packet = null;
        try
        {
            ContainerHost host = peerManager.getLocalPeer().getContainerHostById( containerId );
            packet = new AlertEvent( host.getInitiatorPeerId(), host.getEnvironmentId().getId(), containerId,
                    host.getTemplateName(), alert, buildExpireTime().getTime() );
        }
        catch ( HostNotFoundException e )
        {
            LOG.warn( e.getMessage() );
        }
        //          Example of usage:
        //                QuotaAlertValue quotaAlertValue = alert.getAlertValue( QuotaAlertValue.class );
        //                StringAlertValue stringAlertValue = alert.getAlertValue( StringAlertValue.class );
        //                ExceededQuota v1 = quotaAlertValue.getValue();
        //                String v2 = stringAlertValue.getValue();

        return packet;
    }


    protected Date buildExpireTime()
    {
        return DateUtils.addMinutes( new Date(), ALERT_LIVE_TIME );
    }


    @Override
    public ResourceHostMetrics getResourceHostMetrics()
    {
        ResourceHostMetrics result = new ResourceHostMetrics();

        for ( ResourceHost resourceHost : peerManager.getLocalPeer().getResourceHosts() )
        {

            try
            {
                ResourceHostMetric resourceHostMetric = new ResourceHostMetric( peerManager.getLocalPeer().getId() );
                HostInfo hostInfo = hostRegistry.getHostInfoById( resourceHost.getId() );
                resourceHostMetric.setHostInfo( new ResourceHostInfoModel( hostInfo ) );
                ResourceHostMetric m = fetchResourceHostMetric( resourceHost );
                if ( m != null )
                {
                    resourceHostMetric.updateMetrics( m );
                    resourceHostMetric.setConnected( true );
                    result.addMetric( resourceHostMetric );
                }
            }
            catch ( Exception ignore )
            {
                // ignore
                //                HostInfoModel defaultHostInfo =
                //                        new HostInfoModel( resourceHost.getId(), resourceHost.getHostname(), new
                // HostInterfaces(),
                //                                HostArchitecture.UNKNOWN );
                //                resourceHostMetric.setHostInfo( defaultHostInfo );
                //                resourceHostMetric.setConnected( false );
            }
        }

        return result;
    }


    @Override
    public Collection<AlertEvent> getAlertEvents()
    {
        return Collections.unmodifiableCollection( alerts );
    }


    @Override
    public List<AlertEvent> getAlertsQueue()
    {
        List<AlertEvent> result = new ArrayList<>();
        result.addAll( alertQueue.values() );
        return result;
    }


    protected void notifyAlertListeners()
    {
        for ( AlertEvent alertEvent : alerts )
        {
            if ( !alertEvent.isDelivered() && !alertEvent.isExpired() )
            {
                for ( AlertListener alertListener : alertListeners )
                {
                    AlertNotifier alertNotifier = new AlertNotifier( alertEvent, alertListener );
                    notificationExecutor.submit( alertNotifier );
                }
                alertEvent.setDelivered( true );
            }
        }
    }


    //    @Override
    protected void deliverAlerts()
    {
        for ( AlertEvent alertEvent : alertQueue.values() )
        {
            if ( !alertEvent.isDelivered() )
            {
                deliverAlertPackToPeer( alertEvent );
            }
        }
    }


    private void clearObsoleteAlerts()
    {
        for ( AlertEvent alertEvent : alertQueue.values() )
        {
            if ( alertEvent.isExpired() )
            {
                LOG.debug( String.format( "Alert package '%s' expired. ", alertEvent.getResource().getId() ) );
                // removing obsolete alert
                alertQueue.remove( alertEvent.getResource().getId() );
            }
        }
    }


    private void deliverAlertPackToPeer( final AlertEvent alertEvent )
    {
        Peer peer = null;
        try
        {
            peer = peerManager.getPeer( alertEvent.getPeerId() );
            new AlertDeliver( peer, alertEvent ).run();
        }
        catch ( PeerException e )
        {
            LOG.warn( String.format( "Destination peer '%s' for alert '%s' not found.", alertEvent.getPeerId(),
                    alertEvent.getResource().getId() ) );
        }
    }


    private class BackgroundTasksRunner implements Runnable
    {
        @Override
        public void run()
        {
            LOG.debug( "Background task runner started..." );
            try
            {
                deliverAlerts();
                notifyAlertListeners();
                clearObsoleteAlerts();
            }
            catch ( Exception e )
            {
                LOG.warn( "Background task execution faild: " + e.getMessage() );
            }
            LOG.debug( "Background task runner finished." );
        }
    }


    @Override
    public void onHeartbeat( final ResourceHostInfo resourceHostInfo, final Set<QuotaAlertValue> alerts )
    {
        if ( alerts != null )
        {
            for ( QuotaAlertValue quotaAlertValue : alerts )
            {
                queueAlertResource( new QuotaAlert( quotaAlertValue, System.currentTimeMillis() ) );
            }
        }
    }
}
