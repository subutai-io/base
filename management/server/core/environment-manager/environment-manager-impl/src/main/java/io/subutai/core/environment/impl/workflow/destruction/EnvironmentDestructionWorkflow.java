package io.subutai.core.environment.impl.workflow.destruction;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.servicemix.beanflow.Workflow;

import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.environment.impl.EnvironmentManagerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;
import io.subutai.core.environment.impl.workflow.destruction.steps.CleanupEnvironmentStep;
import io.subutai.core.environment.impl.workflow.destruction.steps.CleanupP2PStep;
import io.subutai.core.environment.impl.workflow.destruction.steps.RemoveKeysStep;


public class EnvironmentDestructionWorkflow extends Workflow<EnvironmentDestructionWorkflow.EnvironmentDestructionPhase>
{

    private static final Logger LOG = LoggerFactory.getLogger( EnvironmentDestructionWorkflow.class );

    private final EnvironmentManagerImpl environmentManager;
    private EnvironmentImpl environment;
    private final boolean forceMetadataRemoval;
    private final TrackerOperation operationTracker;

    private Throwable error;


    public enum EnvironmentDestructionPhase
    {
        INIT,
        CLEANUP_ENVIRONMENT,
        CLEANUP_P2P,
        REMOVE_KEYS,
        FINALIZE
    }


    public EnvironmentDestructionWorkflow( final EnvironmentManagerImpl environmentManager,
                                           final EnvironmentImpl environment, final boolean forceMetadataRemoval,
                                           final TrackerOperation operationTracker )
    {
        super( EnvironmentDestructionPhase.INIT );

        this.environmentManager = environmentManager;
        this.environment = environment;
        this.forceMetadataRemoval = forceMetadataRemoval;
        this.operationTracker = operationTracker;
    }


    //********************* WORKFLOW STEPS ************


    public EnvironmentDestructionPhase INIT()
    {
        operationTracker.addLog( "Initializing environment destruction" );

        environment.setStatus( EnvironmentStatus.UNDER_MODIFICATION );

        environment = environmentManager.update( environment );

        return EnvironmentDestructionPhase.CLEANUP_ENVIRONMENT;
    }


    public EnvironmentDestructionPhase CLEANUP_ENVIRONMENT()
    {
        operationTracker.addLog( "Cleaning up environment" );

        try
        {
            new CleanupEnvironmentStep( environment ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentDestructionPhase.CLEANUP_P2P;
        }
        catch ( Exception e )
        {
            setError( e );

            return null;
        }
    }


    public EnvironmentDestructionPhase CLEANUP_P2P()
    {
        operationTracker.addLog( "Cleaning up P2P" );

        try
        {
            new CleanupP2PStep( environment ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentDestructionPhase.REMOVE_KEYS;
        }
        catch ( Exception e )
        {
            setError( e );

            return null;
        }
    }


    public EnvironmentDestructionPhase REMOVE_KEYS()
    {
        operationTracker.addLog( "Removing keys" );

        try
        {
            new RemoveKeysStep( environment ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentDestructionPhase.FINALIZE;
        }
        catch ( Exception e )
        {
            setError( e );

            return null;
        }
    }


    public void FINALIZE()
    {
        LOG.info( "Finalizing environment destruction" );

        environmentManager.remove( environment );

        operationTracker.addLogDone( "Environment is destroyed" );

        //this is a must have call
        stop();
    }


    public Throwable getError()
    {
        return error;
    }


    public void setError( final Throwable error )
    {
        environment.setStatus( EnvironmentStatus.UNHEALTHY );
        environment = environmentManager.update( environment );
        this.error = error;
        LOG.error( "Error destroying environment", error );
        operationTracker.addLogFailed( error.getMessage() );
        //stop the workflow
        stop();
    }
}
