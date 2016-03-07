package io.subutai.core.peer.cli;


import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.karaf.shell.commands.Command;

import io.subutai.common.host.HostInterface;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.ResourceHost;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;
import io.subutai.core.peer.api.PeerManager;


@Command( scope = "peer", name = "hosts" )
public class HostsCommand extends SubutaiShellCommandSupport
{
    DateFormat fmt = new SimpleDateFormat( "dd.MM.yy HH:mm:ss.SS" );
    private PeerManager peerManager;
    private IdentityManager identityManager;


    public void setPeerManager( final PeerManager peerManager )
    {
        this.peerManager = peerManager;
    }


    public void setIdentityManager( final IdentityManager identityManager )
    {
        this.identityManager = identityManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {

        LocalPeer localPeer = peerManager.getLocalPeer();

        System.out.println( "List of hosts in local peer:" );
        for ( ResourceHost resourceHost : localPeer.getResourceHosts() )
        {
            print( resourceHost, "\t" );
            for ( ContainerHost containerHost : resourceHost.getContainerHosts() )
            {
                print( containerHost, "\t\t" );
            }
        }
        return null;
    }


    protected void print( Host host, String padding ) throws PeerException
    {
        String containerInfo = String.format( "%s ", host.isConnected() ? " CONNECTED" : " DISCONNECTED" );
        if ( host instanceof ContainerHost )
        {
            ContainerHost c = ( ContainerHost ) host;
            containerInfo += c.getState();
        }

        System.out
                .println( String.format( "%s+--%s %s %s", padding, host.getHostname(), host.getId(), containerInfo ) );
        for ( HostInterface hostInterface : host.getHostInterfaces().getAll() )
        {
            System.out.println( String.format( "\t%s %s", hostInterface.getName(), hostInterface.getIp() ) );
        }
    }
}
