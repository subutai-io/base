package io.subutai.core.environment.impl.workflow.modification;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.servicemix.beanflow.Workflow;

import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.environment.impl.EnvironmentManagerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;
import io.subutai.core.environment.impl.workflow.creation.steps.RemoveSshKeyStep;


public class SshKeyRemovalWorkflow extends Workflow<SshKeyRemovalWorkflow.SshKeyAdditionPhase>
{
    private static final Logger LOG = LoggerFactory.getLogger( SshKeyRemovalWorkflow.class );

    private EnvironmentImpl environment;
    private final String sshKey;
    private final TrackerOperation operationTracker;
    private final EnvironmentManagerImpl environmentManager;

    private Throwable error;


    public enum SshKeyAdditionPhase
    {
        INIT, REMOVE_KEY, FINALIZE
    }


    public SshKeyRemovalWorkflow( final EnvironmentImpl environment, final String sshKey,
                                  final TrackerOperation operationTracker,
                                  final EnvironmentManagerImpl environmentManager )
    {
        super( SshKeyAdditionPhase.INIT );

        this.environment = environment;
        this.sshKey = sshKey;
        this.operationTracker = operationTracker;
        this.environmentManager = environmentManager;
    }


    //********************* WORKFLOW STEPS ************


    public SshKeyAdditionPhase INIT()
    {
        operationTracker.addLog( "Initializing ssh key removal" );

        environment.setStatus( EnvironmentStatus.UNDER_MODIFICATION );

        environment = environmentManager.update( environment );

        return SshKeyAdditionPhase.REMOVE_KEY;
    }


    public SshKeyAdditionPhase REMOVE_KEY()
    {

        operationTracker.addLog( "Removing ssh key from containers" );

        try
        {
            new RemoveSshKeyStep( sshKey, environment, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return SshKeyAdditionPhase.FINALIZE;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public void FINALIZE()
    {
        LOG.info( "Finalizing ssh key removal" );

        environment.setStatus( EnvironmentStatus.HEALTHY );

        environment = environmentManager.update( environment );

        operationTracker.addLogDone( "Ssh key is removed" );

        //this is a must have call
        stop();
    }


    @Override
    public void fail( final String message, final Throwable e )
    {
        super.fail( message, e );
        saveFailState();
    }


    private void saveFailState()
    {
        environment.setStatus( EnvironmentStatus.UNHEALTHY );
        environment = environmentManager.update( environment );
        operationTracker.addLogFailed( getFailedReason() );
        LOG.error( "Error removing ssh key", getFailedException() );
    }
}
