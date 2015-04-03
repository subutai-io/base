package org.safehaus.subutai.core.env.impl.tasks;


import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.safehaus.subutai.common.environment.EnvironmentNotFoundException;
import org.safehaus.subutai.common.environment.EnvironmentStatus;
import org.safehaus.subutai.common.mdc.SubutaiExecutors;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.common.peer.ContainersDestructionResult;
import org.safehaus.subutai.common.peer.Peer;
import org.safehaus.subutai.common.peer.PeerException;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.common.util.CollectionUtil;
import org.safehaus.subutai.common.util.ExceptionUtil;
import org.safehaus.subutai.core.env.api.exception.EnvironmentDestructionException;
import org.safehaus.subutai.core.env.impl.EnvironmentManagerImpl;
import org.safehaus.subutai.core.env.impl.entity.EnvironmentImpl;
import org.safehaus.subutai.core.env.impl.exception.ResultHolder;
import org.safehaus.subutai.core.peer.api.LocalPeer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;


/**
 * Destroys Environment with all values carried with it. Destroys all container hosts across peers if any.
 *
 * @see org.safehaus.subutai.core.env.impl.entity.EnvironmentImpl
 * @see org.safehaus.subutai.core.env.impl.EnvironmentManagerImpl
 * @see org.safehaus.subutai.core.env.impl.exception.ResultHolder
 * @see java.lang.Runnable
 */
public class DestroyEnvironmentTask implements Runnable
{
    private static final Logger LOG = LoggerFactory.getLogger( DestroyEnvironmentTask.class.getName() );

    private final EnvironmentManagerImpl environmentManager;
    private final EnvironmentImpl environment;
    private final Set<Throwable> exceptions;
    private final ResultHolder<EnvironmentDestructionException> resultHolder;
    private final boolean forceMetadataRemoval;
    private final LocalPeer localPeer;
    private final Semaphore semaphore;
    private final TrackerOperation op;
    private ExceptionUtil exceptionUtil = new ExceptionUtil();


    public DestroyEnvironmentTask( final EnvironmentManagerImpl environmentManager, final EnvironmentImpl environment,
                                   final Set<Throwable> exceptions,
                                   final ResultHolder<EnvironmentDestructionException> resultHolder,
                                   final boolean forceMetadataRemoval, final LocalPeer localPeer,
                                   final TrackerOperation op )
    {
        this.environmentManager = environmentManager;
        this.environment = environment;
        this.exceptions = exceptions;
        this.resultHolder = resultHolder;
        this.forceMetadataRemoval = forceMetadataRemoval;
        this.localPeer = localPeer;
        this.op = op;
        this.semaphore = new Semaphore( 0 );
    }


    @Override
    public void run()
    {
        try
        {

            if ( environment.getStatus() == EnvironmentStatus.EMPTY || environment.getContainerHosts().isEmpty() )
            {
                environmentManager.removeEnvironment( environment.getId(), false );

                op.addLogDone( "Environment destroyed successfully" );

                return;
            }

            environment.setStatus( EnvironmentStatus.UNDER_MODIFICATION );

            Set<Peer> environmentPeers = Sets.newHashSet();

            for ( ContainerHost container : environment.getContainerHosts() )
            {
                environmentPeers.add( container.getPeer() );
            }

            ExecutorService executorService = SubutaiExecutors.newFixedThreadPool( environmentPeers.size() );

            Set<Future<ContainersDestructionResult>> futures = Sets.newHashSet();

            op.addLog( "Destroying environment containers..." );

            for ( Peer peer : environmentPeers )
            {
                futures.add(
                        executorService.submit( new PeerEnvironmentDestructionTask( peer, environment.getId() ) ) );
            }

            Set<ContainersDestructionResult> results = Sets.newHashSet();

            for ( Future<ContainersDestructionResult> future : futures )
            {
                try
                {
                    results.add( future.get() );
                }
                catch ( ExecutionException | InterruptedException e )
                {
                    Throwable cause = exceptionUtil.getRootCause( e );

                    exceptions.add( cause );

                    op.addLog( String.format( "Error destroying containers: %s", cause.getMessage() ) );
                }
            }

            executorService.shutdown();

            for ( ContainersDestructionResult result : results )
            {
                boolean deleteAllPeerContainers = false;
                if ( !Strings.isNullOrEmpty( result.getException() ) )
                {

                    if ( result.getException().equals( "Container group not found" ) )
                    {
                        deleteAllPeerContainers = true;
                    }
                    else
                    {
                        op.addLog( String.format( "Error destroying containers: %s", result.getException() ) );

                        exceptions.add( new EnvironmentDestructionException( result.getException() ) );
                    }
                }
                if ( deleteAllPeerContainers )
                {
                    for ( ContainerHost containerHost : environment.getContainerHosts() )
                    {
                        if ( containerHost.getPeerId().equals( result.peerId().toString() ) )
                        {
                            environment.removeContainer( containerHost.getId() );

                            environmentManager.notifyOnContainerDestroyed( environment, containerHost.getId() );
                        }
                    }
                }
                else if ( !CollectionUtil.isCollectionEmpty( result.getDestroyedContainersIds() ) )
                {
                    for ( UUID containerId : result.getDestroyedContainersIds() )
                    {
                        environment.removeContainer( containerId );

                        environmentManager.notifyOnContainerDestroyed( environment, containerId );
                    }
                }
            }

            if ( !forceMetadataRemoval )
            {
                environmentPeers.removeAll( environment.getPeers() );
            }

            //remove certificates
            for ( Peer peer : environmentPeers )
            {
                if ( !peer.isLocal() )
                {
                    try
                    {
                        op.addLog( String.format( "Removing environment certificate on peer %s...", peer.getName() ) );

                        peer.removeEnvironmentCertificates( environment.getId() );
                    }
                    catch ( PeerException e )
                    {
                        Throwable cause = exceptionUtil.getRootCause( e );

                        op.addLog(
                                String.format( "Error removing environment certificate on peer %s: %s", peer.getName(),
                                        cause.getMessage() ) );

                        exceptions.add( cause );
                    }
                }
            }

            if ( forceMetadataRemoval || environment.getContainerHosts().isEmpty() )
            {
                try
                {
                    op.addLog( "Removing environment certificate on local peer..." );

                    localPeer.removeEnvironmentCertificates( environment.getId() );
                }
                catch ( PeerException e )
                {
                    op.addLog( String.format( "Error removing environment certificate on local peer: %s",
                            e.getMessage() ) );

                    LOG.error( "Error removing environment certificate from local peer", e );
                }
                environmentManager.removeEnvironment( environment.getId(), false );

                op.addLogDone( "Environment destroyed successfully" );
            }
            else
            {
                environment.setStatus( EnvironmentStatus.UNHEALTHY );

                op.addLogDone( "Environment destroyed partially" );
            }
        }
        catch ( EnvironmentNotFoundException e )
        {
            LOG.error( String.format( "Error destroying environment %s", environment.getId() ), e );

            resultHolder.setResult( new EnvironmentDestructionException( e ) );

            op.addLogFailed( String.format( "Error destroying environment: %s", e.getMessage() ) );
        }
        finally
        {
            semaphore.release();
        }
    }


    public void waitCompletion() throws InterruptedException
    {
        semaphore.acquire();
    }
}
