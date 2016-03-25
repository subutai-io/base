package io.subutai.common.command;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import io.subutai.common.peer.Host;
import io.subutai.common.util.CollectionUtil;


/**
 * Provide utility functions for command execution
 */
public class CommandUtil
{
    /**
     * Allows to execute command on host. Throws CommandException if command has completed with status other then
     * SUCCEEDED.
     *
     * @param requestBuilder - request
     * @param host - host
     *
     * @return -  command result
     *
     * @throws CommandException - exception thrown if something went wrong
     */
    public CommandResult execute( RequestBuilder requestBuilder, Host host ) throws CommandException
    {

        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkNotNull( host );

        CommandResult result = host.execute( requestBuilder );

        if ( !result.hasSucceeded() )
        {
            throw new CommandException( String.format( "Error executing command on host %s: %s", host.getHostname(),
                    result.hasCompleted() ? result.getStdErr() : "Command timed out" ) );
        }
        return result;
    }


    /**
     * Execute request on host with callback. Allows to stop callback from being triggered by calling stop() from inside
     * a callback. Please make sure that the command is not a daemon command (command which forks a daemon process).
     * Otherwise please set request as a daemon request by calling RequestBuilder.daemon()
     *
     * @param requestBuilder - request
     * @param host - host
     * @param callback - stoppable callback
     *
     * @throws CommandException - exception thrown if something went wrong
     */
    public void executeAsync( RequestBuilder requestBuilder, Host host, final StoppableCallback callback )
            throws CommandException
    {

        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkNotNull( host );
        Preconditions.checkNotNull( callback );

        host.executeAsync( requestBuilder, new CommandCallback()
        {
            @Override
            public void onResponse( final Response response, final CommandResult commandResult )
            {
                if ( !callback.isStopped() )
                {
                    callback.onResponse( response, commandResult );
                }
            }
        } );
    }


    /**
     * Allows to execute the same command on multiple hosts in parallel, with the same callback for responses from each
     * host
     *
     * @param requestBuilder - request
     * @param hosts - hosts
     * @param callback - callback
     *
     * @throws CommandException - exception thrown if something went wrong
     */

    public void executeAsync( RequestBuilder requestBuilder, Set<Host> hosts, final CommandCallback callback )
            throws CommandException
    {

        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( hosts ) );
        Preconditions.checkNotNull( callback );

        final ReentrantLock lock = new ReentrantLock( true );

        for ( Host host : hosts )
        {
            host.executeAsync( requestBuilder, new CommandCallback()
            {
                @Override
                public void onResponse( final Response response, final CommandResult commandResult )
                {
                    lock.lock();
                    try
                    {
                        callback.onResponse( response, commandResult );
                    }
                    finally
                    {
                        lock.unlock();
                    }
                }
            } );
        }
    }


    /**
     * Allows to execute command on each host one-by-one
     *
     * @param requestBuilder - request
     * @param hosts - hosts
     *
     * @return -  map containing command results
     *
     * @throws CommandException - exception thrown if something went wrong
     */
    public Map<Host, CommandResult> executeSequential( RequestBuilder requestBuilder, Set<Host> hosts )
            throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( hosts ) );

        Map<Host, CommandResult> resultMap = Maps.newHashMap();

        for ( Host host : hosts )
        {
            resultMap.put( host, host.execute( requestBuilder ) );
        }

        return resultMap;
    }


    /**
     * Allows to execute command on all hosts in parallel
     *
     * @param requestBuilder - request
     * @param hosts - hosts
     *
     * @return -  map containing command results
     *
     * @throws CommandException - exception thrown if something went wrong
     */
    public Map<Host, CommandResult> executeParallel( RequestBuilder requestBuilder, Set<Host> hosts )
            throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( hosts ) );

        final Map<Host, CommandResult> resultMap = Maps.newConcurrentMap();
        ExecutorService taskExecutor = Executors.newFixedThreadPool( hosts.size() );
        CompletionService<HostCommandResult> taskCompletionService = new ExecutorCompletionService<>( taskExecutor );

        for ( Host host : hosts )
        {
            taskCompletionService.submit( new CommandTask( host, requestBuilder ) );
        }

        for ( int i = 0; i < hosts.size(); i++ )
        {
            try
            {
                Future<HostCommandResult> result = taskCompletionService.take();
                HostCommandResult hostCommandResult = result.get();
                resultMap.put( hostCommandResult.getHost(), hostCommandResult.getCommandResult() );
            }
            catch ( ExecutionException | InterruptedException e )
            {
                throw new CommandException( e );
            }
        }

        taskExecutor.shutdown();

        return resultMap;
    }


    /**
     * Allows to execute command on all hosts in parallel. If any exception is thrown, ignores is and collects results
     * of only succeeded executions
     *
     * @param requestBuilder - request
     * @param hosts - hosts
     *
     * @return -  map containing command results
     */
    public Map<Host, CommandResult> executeParallelSilent( RequestBuilder requestBuilder, Set<Host> hosts )

    {
        Preconditions.checkNotNull( requestBuilder );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( hosts ) );

        final Map<Host, CommandResult> resultMap = Maps.newConcurrentMap();
        ExecutorService taskExecutor = Executors.newFixedThreadPool( hosts.size() );
        CompletionService<HostCommandResult> taskCompletionService = new ExecutorCompletionService<>( taskExecutor );

        for ( Host host : hosts )
        {
            taskCompletionService.submit( new CommandTask( host, requestBuilder ) );
        }

        for ( int i = 0; i < hosts.size(); i++ )
        {
            try
            {
                Future<HostCommandResult> result = taskCompletionService.take();
                HostCommandResult hostCommandResult = result.get();
                resultMap.put( hostCommandResult.getHost(), hostCommandResult.getCommandResult() );
            }
            catch ( Exception e )
            {
                //ignore
            }
        }

        taskExecutor.shutdown();

        return resultMap;
    }


    private class HostCommandResult
    {
        private Host host;
        private CommandResult commandResult;


        public HostCommandResult( final Host host, final CommandResult commandResult )
        {
            this.host = host;
            this.commandResult = commandResult;
        }


        public Host getHost()
        {
            return host;
        }


        public CommandResult getCommandResult()
        {
            return commandResult;
        }
    }


    private class CommandTask implements Callable<HostCommandResult>
    {
        private Host host;
        private RequestBuilder requestBuilder;


        public CommandTask( final Host host, final RequestBuilder requestBuilder )
        {
            this.host = host;
            this.requestBuilder = requestBuilder;
        }


        @Override
        public HostCommandResult call() throws Exception
        {
            return new HostCommandResult( host, host.execute( requestBuilder ) );
        }
    }


    /**
     * Callback that can be stopped from being triggered by calling stop() from inside onSuccess method
     */
    public static abstract class StoppableCallback implements CommandCallback
    {

        private final AtomicBoolean stopped = new AtomicBoolean( false );


        public final boolean isStopped()
        {
            return stopped.get();
        }


        public final void stop()
        {
            stopped.set( true );
        }
    }
}
