package io.subutai.core.environment.cli;


import java.util.UUID;

import io.subutai.common.util.UUIDUtil;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;


/**
 * Remove environment metadata from database and notify trigger environment destroyed event
 */
@Command( scope = "env", name = "remove", description = "Command to remove environment from database" )
public class RemoveEnvironmentCommand extends SubutaiShellCommandSupport
{
    @Argument( name = "envId", description = "Environment id",
            index = 0, multiValued = false, required = true )
    String environmentId;

    private final EnvironmentManager environmentManager;


    public RemoveEnvironmentCommand( final EnvironmentManager environmentManager )
    {
        Preconditions.checkNotNull( environmentManager );

        this.environmentManager = environmentManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        Preconditions.checkArgument( UUIDUtil.isStringAUuid( environmentId ), "Invalid environment id" );

        environmentManager.removeEnvironment( UUID.fromString( environmentId ) );

        System.out.println( "Environment removed from database" );

        return null;
    }
}
