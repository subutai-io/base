package io.subutai.core.network.cli;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.subutai.common.peer.LocalPeer;
import io.subutai.common.protocol.Tunnel;
import io.subutai.common.protocol.Tunnels;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;


@Command( scope = "net", name = "tunnel-list", description = "Lists tunnels" )
public class ListTunnelCommand extends SubutaiShellCommandSupport
{
    private static final Logger LOG = LoggerFactory.getLogger( ListTunnelCommand.class.getName() );

    private final LocalPeer localPeer;

    @Argument( index = 0, name = "host id", required = false, multiValued = false,
            description = "host id" )
    String hostId;


    public ListTunnelCommand( final LocalPeer localPeer )
    {
        Preconditions.checkNotNull( localPeer );

        this.localPeer = localPeer;
    }


    @Override
    protected Object doExecute()
    {

        try
        {
            Tunnels tunnels = Strings.isNullOrEmpty( hostId ) ? localPeer.getManagementHost().getTunnels() :
                              localPeer.getResourceHostById( hostId ).getTunnels();

            System.out.format( "Found %d tunnel(s)%n", tunnels.getTunnels().size() );

            for ( Tunnel tunnel : tunnels.getTunnels() )
            {
                System.out.format( "%s %s %d %d%n", tunnel.getTunnelName(), tunnel.getTunnelIp(), tunnel.getVlan(),
                        tunnel.getVni() );
            }
        }
        catch ( Exception e )
        {
            System.out.println( e.getMessage() );
            LOG.error( "Error in ListTunnelCommand", e );
        }

        return null;
    }
}
