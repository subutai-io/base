package io.subutai.core.environment.impl.workflow;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.subutai.common.peer.Peer;
import io.subutai.common.util.CollectionUtil;


public class PeerUtil<T>
{
    private static final Logger LOG = LoggerFactory.getLogger( PeerUtil.class );

    private final Set<PeerTask<T>> peerTasks = Sets.newHashSet();


    /**
     * Adds a task for a peer
     *
     * @param peerTask {@code PeerTask}
     */
    public void addPeerTask( PeerTask<T> peerTask )
    {
        Preconditions.checkNotNull( peerTask, "Invalid peer task" );

        peerTasks.add( peerTask );
    }


    /**
     * Executes added tasks in parallel
     *
     * @return set of {@code PeerTaskResult}
     */
    public Set<PeerTaskResult<T>> executeParallel()
    {
        Preconditions
                .checkArgument( !CollectionUtil.isCollectionEmpty( peerTasks ), "No peer task found for execution" );

        Set<PeerTaskResult<T>> results = executeParallel( peerTasks );

        peerTasks.clear();

        return results;
    }


    protected Set<PeerTaskResult<T>> executeParallel( Set<PeerTask<T>> peerTasks )
    {
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( peerTasks ) );

        Set<PeerTaskResult<T>> peerTaskResults = Sets.newHashSet();

        ExecutorService taskExecutor = Executors.newFixedThreadPool( peerTasks.size() );
        CompletionService<T> taskCompletionService = new ExecutorCompletionService<>( taskExecutor );

        Map<Peer, Future<T>> peerFutures = Maps.newHashMap();

        for ( PeerTask<T> peerTask : peerTasks )
        {
            peerFutures.put( peerTask.getPeer(), taskCompletionService.submit( peerTask.getTask() ) );
        }

        for ( Map.Entry<Peer, Future<T>> peerFutureEntry : peerFutures.entrySet() )
        {
            Future<T> peerFuture = peerFutureEntry.getValue();
            Peer targetPeer = peerFutureEntry.getKey();

            try
            {
                peerTaskResults.add( new PeerTaskResult<>( targetPeer, peerFuture.get() ) );
            }
            catch ( Exception e )
            {
                LOG.error( "Error executing task on peer %s", targetPeer.getName(), e );

                peerTaskResults.add( new PeerTaskResult<T>( targetPeer, e ) );
            }
        }

        return peerTaskResults;
    }


    public static class PeerTask<T>
    {

        private final Peer peer;
        private final Callable<T> task;


        public PeerTask( final Peer peer, final Callable<T> task )
        {
            this.peer = peer;
            this.task = task;
        }


        public Peer getPeer()
        {
            return peer;
        }


        public Callable<T> getTask()
        {
            return task;
        }
    }


    public static class PeerTaskResult<T>
    {
        private final Peer peer;
        private T result;
        private Exception exception;
        private boolean hasSucceeded = true;


        protected PeerTaskResult( final Peer peer, final T result )
        {
            this.peer = peer;
            this.result = result;
        }


        protected PeerTaskResult( final Peer peer, final Exception exception )
        {
            this.peer = peer;
            this.exception = exception;
            this.hasSucceeded = false;
        }


        public Peer getPeer()
        {
            return peer;
        }


        public T getResult()
        {
            return result;
        }


        public Exception getException()
        {
            return exception;
        }


        public String getFailureReason()
        {
            return exception == null ? "Unknown" : exception.getMessage();
        }


        public boolean hasSucceeded()
        {
            return hasSucceeded;
        }
    }
}
