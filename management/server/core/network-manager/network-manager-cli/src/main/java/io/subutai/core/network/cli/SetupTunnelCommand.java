package io.subutai.core.network.cli;


import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;
import io.subutai.core.network.api.NetworkManager;
import io.subutai.core.network.api.NetworkManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;


@Command( scope = "net", name = "tunnel-create", description = "Creates tunnel with peer" )
public class SetupTunnelCommand extends SubutaiShellCommandSupport
{
    private static final Logger LOG = LoggerFactory.getLogger( SetupTunnelCommand.class.getName() );

    private final NetworkManager networkManager;
    @Argument( index = 0, name = "tunnel id", required = true, multiValued = false,
            description = "tunnel id" )
    int tunnelId;
    @Argument( index = 1, name = "tunnel ip", required = true, multiValued = false,
            description = "tunnel ip" )
    String tunnelIp;


    public SetupTunnelCommand( final NetworkManager networkManager )
    {
        Preconditions.checkNotNull( networkManager );

        this.networkManager = networkManager;
    }


    @Override
    protected Object doExecute()
    {

        try
        {
            networkManager.setupTunnel( tunnelId, tunnelIp );
            System.out.println( "OK" );
        }
        catch ( NetworkManagerException e )
        {
            System.out.println( e.getMessage() );
            LOG.error( "Error in SetupTunnelCommand", e );
        }

        return null;
    }
}
