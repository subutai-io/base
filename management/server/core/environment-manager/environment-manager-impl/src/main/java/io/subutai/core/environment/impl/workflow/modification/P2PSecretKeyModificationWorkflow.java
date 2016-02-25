package io.subutai.core.environment.impl.workflow.modification;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.servicemix.beanflow.Workflow;

import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.environment.impl.EnvironmentManagerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;
import io.subutai.core.environment.impl.workflow.modification.steps.P2PSecretKeyResetStep;


public class P2PSecretKeyModificationWorkflow
        extends Workflow<P2PSecretKeyModificationWorkflow.P2PSecretKeyModificationPhase>
{
    private static final Logger LOG = LoggerFactory.getLogger( P2PSecretKeyModificationWorkflow.class );

    private EnvironmentImpl environment;
    private final String p2pSecretKey;
    private final long p2pSecretKeyTtlSeconds;
    private final TrackerOperation operationTracker;
    private final EnvironmentManagerImpl environmentManager;

    private Throwable error;


    public static enum P2PSecretKeyModificationPhase
    {
        INIT, REPLACE_KEY, FINALIZE
    }


    public P2PSecretKeyModificationWorkflow( final EnvironmentImpl environment, final String p2pSecretKey,
                                             final long p2pSecretKeyTtlSeconds, final TrackerOperation operationTracker,
                                             final EnvironmentManagerImpl environmentManager )
    {
        super( P2PSecretKeyModificationPhase.INIT );

        this.environment = environment;
        this.p2pSecretKey = p2pSecretKey;
        this.p2pSecretKeyTtlSeconds = p2pSecretKeyTtlSeconds;
        this.operationTracker = operationTracker;
        this.environmentManager = environmentManager;
    }


    //********************* WORKFLOW STEPS ************


    public P2PSecretKeyModificationPhase INIT()
    {
        operationTracker.addLog( "Initializing P2P secret key modification" );

        environment.setStatus( EnvironmentStatus.UNDER_MODIFICATION );

        environment = environmentManager.saveOrUpdate( environment );

        return P2PSecretKeyModificationPhase.REPLACE_KEY;
    }


    public P2PSecretKeyModificationPhase REPLACE_KEY()
    {

        operationTracker.addLog( "Modifying P2P secret key on peers" );

        try
        {
            new P2PSecretKeyResetStep( environment,
                    new P2PCredentials( environment.getTunnelCommunityName(), p2pSecretKey, p2pSecretKeyTtlSeconds ) )
                    .execute();

            environment = environmentManager.saveOrUpdate( environment );

            return P2PSecretKeyModificationPhase.FINALIZE;
        }
        catch ( Exception e )
        {
            setError( e );

            return null;
        }
    }


    public void FINALIZE()
    {
        LOG.info( "Finalizing P2P secret key modification" );

        environment.setStatus( EnvironmentStatus.HEALTHY );

        environment = environmentManager.saveOrUpdate( environment );

        operationTracker.addLogDone( "P2P secret key is modified" );

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

        environment = environmentManager.saveOrUpdate( environment );

        this.error = error;
        LOG.error( "Error modifying P2P secret key", error );
        operationTracker.addLogFailed( error.getMessage() );
        //stop the workflow
        stop();
    }
}
