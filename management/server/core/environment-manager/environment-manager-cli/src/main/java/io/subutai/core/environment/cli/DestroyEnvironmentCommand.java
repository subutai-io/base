package io.subutai.core.environment.cli;


import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;

import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;


/**
 * Destroys environment
 */
@Command( scope = "environment", name = "destroy", description = "Command to destroy environment" )
public class DestroyEnvironmentCommand extends SubutaiShellCommandSupport
{
    @Argument( name = "envId", description = "Environment id",
            index = 0, multiValued = false, required = true )
    /**
     * {@value environmentId} target environment id to destroy
     * {@code required = true}
     */
            String environmentId;

    @Argument( name = "async", description = "asynchronous destruction",
            index = 1, multiValued = false, required = false )
    /**
     * {@value async} execute destroy environment asynchronously
     * <p> {@code required = false}, {@code default = false}</p>
     */
            boolean async = false;

    private final EnvironmentManager environmentManager;


    public DestroyEnvironmentCommand( final EnvironmentManager environmentManager )
    {
        Preconditions.checkNotNull( environmentManager );

        this.environmentManager = environmentManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        environmentManager.destroyEnvironment( environmentId, async );

        System.out.println( "Environment destroyed" );

        return null;
    }
}
