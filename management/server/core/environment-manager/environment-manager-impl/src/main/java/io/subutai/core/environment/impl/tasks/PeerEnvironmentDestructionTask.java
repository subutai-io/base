package io.subutai.core.environment.impl.tasks;


import java.util.UUID;
import java.util.concurrent.Callable;

import io.subutai.common.peer.ContainersDestructionResult;
import io.subutai.common.peer.Peer;

import com.google.common.base.Preconditions;


/**
 * PeerEnvironmentDestructionTask destroys Environment on Peer
 *
 * @see io.subutai.common.peer.Peer
 * @see io.subutai.common.environment.Environment
 */
public class PeerEnvironmentDestructionTask implements Callable<ContainersDestructionResult>
{
    private final Peer peer;
    private final UUID environmentId;


    public PeerEnvironmentDestructionTask( final Peer peer, final UUID environmentId )
    {
        Preconditions.checkNotNull( peer );
        Preconditions.checkNotNull( environmentId );

        this.peer = peer;
        this.environmentId = environmentId;
    }


    @Override
    public ContainersDestructionResult call() throws Exception
    {
        return peer.destroyEnvironmentContainers( environmentId );
    }
}
