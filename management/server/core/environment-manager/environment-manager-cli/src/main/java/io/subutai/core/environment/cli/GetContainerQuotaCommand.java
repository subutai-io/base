package io.subutai.core.environment.cli;


import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import io.subutai.common.environment.Environment;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.quota.ContainerResource;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;


@Command( scope = "environment", name = "get-quota", description = "gets quota information from peer for container" )
public class GetContainerQuotaCommand extends SubutaiShellCommandSupport
{

    @Argument( index = 0, name = "environment id", multiValued = false, required = true, description = "Id of "
            + "environment" )
    protected String environmentId;

    @Argument( index = 1, name = "container id", multiValued = false, required = true, description = "container "
            + "id" )
    protected String containerId;

    private EnvironmentManager environmentManager;


    public GetContainerQuotaCommand( EnvironmentManager environmentManager )
    {
        this.environmentManager = environmentManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        Environment environment = environmentManager.loadEnvironment( environmentId );

        ContainerHost targetContainer = environment.getContainerHostById( containerId );
        if ( targetContainer == null )
        {
            System.out.println( "Couldn't get container host by name: " + containerId );
        }
        else
        {
            final ContainerQuota quota = targetContainer.getAvailableQuota();
            for ( ContainerResource resource : quota.getAllResources() )
            {
                System.out.println(
                        String.format( "%s\t%s", resource.getContainerResourceType(), resource.getPrintValue() ) );
            }
        }
        return null;
    }
}
