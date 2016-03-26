package io.subutai.core.environment.impl.workflow.modification;


import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.servicemix.beanflow.Workflow;

import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.environment.Topology;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.environment.impl.EnvironmentManagerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;
import io.subutai.core.environment.impl.workflow.creation.steps.ContainerCloneStep;
import io.subutai.core.environment.impl.workflow.creation.steps.PrepareTemplatesStep;
import io.subutai.core.environment.impl.workflow.creation.steps.RegisterHostsStep;
import io.subutai.core.environment.impl.workflow.creation.steps.RegisterSshStep;
import io.subutai.core.environment.impl.workflow.modification.steps.ContainerDestroyStep;
import io.subutai.core.environment.impl.workflow.modification.steps.PEKGenerationStep;
import io.subutai.core.environment.impl.workflow.modification.steps.SetupP2PStep;
import io.subutai.core.environment.impl.workflow.modification.steps.VNISetupStep;
import io.subutai.core.kurjun.api.TemplateManager;
import io.subutai.core.peer.api.PeerManager;


public class EnvironmentModifyWorkflow extends Workflow<EnvironmentModifyWorkflow.EnvironmentGrowingPhase>
{

    private static final Logger LOG = LoggerFactory.getLogger( EnvironmentModifyWorkflow.class );

    private final TemplateManager templateRegistry;
    private final PeerManager peerManager;
    private EnvironmentImpl environment;
    private final Topology topology;
    private List<String> removedContainers;
    private final String defaultDomain;
    private final TrackerOperation operationTracker;
    private final EnvironmentManagerImpl environmentManager;
    private boolean forceMetadataRemoval;


    //environment creation phases
    public static enum EnvironmentGrowingPhase
    {
        INIT,
        DESTROY_CONTAINERS,
        GENERATE_KEYS,
        SETUP_VNI,
        SETUP_P2P,
        PREPARE_TEMPLATES,
        CLONE_CONTAINERS,
        CONFIGURE_HOSTS,
        CONFIGURE_SSH,
        FINALIZE

    }


    public EnvironmentModifyWorkflow( String defaultDomain, TemplateManager templateRegistry, PeerManager peerManager,
                                      EnvironmentImpl environment, Topology topology, List<String> removedContainers,
                                      TrackerOperation operationTracker, EnvironmentManagerImpl environmentManager,
                                      boolean forceMetadataRemoval )
    {

        super( EnvironmentGrowingPhase.INIT );

        this.templateRegistry = templateRegistry;
        this.peerManager = peerManager;
        this.environment = environment;
        this.topology = topology;
        this.operationTracker = operationTracker;
        this.defaultDomain = defaultDomain;
        this.environmentManager = environmentManager;
        this.removedContainers = new ArrayList<>();
        this.forceMetadataRemoval = false;
        this.removedContainers = removedContainers;
        this.forceMetadataRemoval = forceMetadataRemoval;
    }


    //********************* WORKFLOW STEPS ************


    public EnvironmentGrowingPhase INIT()
    {
        operationTracker.addLog( "Initializing environment growth" );

        environment.setStatus( EnvironmentStatus.UNDER_MODIFICATION );

        environment = environmentManager.update( environment );

        return EnvironmentGrowingPhase.DESTROY_CONTAINERS;
    }


    public EnvironmentGrowingPhase DESTROY_CONTAINERS()
    {
        operationTracker.addLog( "Removing containers" );

        try
        {
            new ContainerDestroyStep( environment, environmentManager, removedContainers, forceMetadataRemoval,
                    operationTracker ).execute();

            environment = environmentManager.update( environment );

            if ( topology == null )
            {
                return EnvironmentGrowingPhase.FINALIZE;
            }

            return EnvironmentGrowingPhase.GENERATE_KEYS;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase GENERATE_KEYS()
    {
        operationTracker.addLog( "Generating PEKs" );

        try
        {
            new PEKGenerationStep( topology, environment, peerManager, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.SETUP_VNI;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase SETUP_VNI()
    {
        operationTracker.addLog( "Setting up VNI" );

        try
        {
            new VNISetupStep( topology, environment, peerManager, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.SETUP_P2P;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase SETUP_P2P()
    {
        operationTracker.addLog( "Setting up P2P" );

        try
        {
            new SetupP2PStep( topology, environment, peerManager, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.PREPARE_TEMPLATES;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase PREPARE_TEMPLATES()
    {
        operationTracker.addLog( "Cloning containers" );

        try
        {
            new PrepareTemplatesStep( peerManager, topology, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.CLONE_CONTAINERS;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );
            return null;
        }
    }


    public EnvironmentGrowingPhase CLONE_CONTAINERS()
    {
        operationTracker.addLog( "Cloning containers" );

        try
        {
            new ContainerCloneStep( defaultDomain, topology, environment, peerManager, environmentManager,
                    operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.CONFIGURE_HOSTS;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase CONFIGURE_HOSTS()
    {
        operationTracker.addLog( "Configuring /etc/hosts" );

        try
        {
            new RegisterHostsStep( environment, operationTracker ).execute();

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.CONFIGURE_SSH;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public EnvironmentGrowingPhase CONFIGURE_SSH()
    {
        operationTracker.addLog( "Configuring ssh" );

        try
        {
            new RegisterSshStep( environment, operationTracker ).execute( environment.getSshKeys() );

            environment = environmentManager.update( environment );

            return EnvironmentGrowingPhase.FINALIZE;
        }
        catch ( Exception e )
        {
            fail( e.getMessage(), e );

            return null;
        }
    }


    public void FINALIZE()
    {
        LOG.info( "Finalizing environment growth" );

        environment.setStatus( EnvironmentStatus.HEALTHY );

        environment = environmentManager.update( environment );

        operationTracker.addLogDone( "Environment is grown" );

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
        LOG.error( "Error modifying environment", getFailedException() );
    }
}
