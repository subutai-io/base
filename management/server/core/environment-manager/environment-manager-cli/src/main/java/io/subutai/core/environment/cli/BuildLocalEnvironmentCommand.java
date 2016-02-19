package io.subutai.core.environment.cli;


import java.util.Set;
import java.util.UUID;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import io.subutai.common.environment.Blueprint;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.NodeGroup;
import io.subutai.common.environment.Topology;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.protocol.PlacementStrategy;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;
import io.subutai.core.peer.api.PeerManager;


@Command( scope = "environment", name = "build-local", description = "Command to build environment on local peer" )
public class BuildLocalEnvironmentCommand extends SubutaiShellCommandSupport
{

    @Argument( name = "templateName", description = "Template name",
            index = 0, multiValued = false, required = true )
    /**
     * {@value templateName} template to clone for environment hosts
     * {@code required = true}
     */
            String templateName;


    @Argument( name = "numberOfContainers", description = "Number of containers",
            index = 1, multiValued = false, required = true )
    /**
     * {@value numberOfContainers }number of container hosts to create in environment
     * {@code required = true}
     */
            int numberOfContainers;
    @Argument( name = "subnetCidr", description = "Subnet in CIDR notation",
            index = 2, multiValued = false, required = true )
    /**
     * {@value subnetCidr } Subnet in CIDR notation
     * {@code required = true}
     */
            String subnetCidr;

    @Argument( name = "async", description = "asynchronous build",
            index = 3, multiValued = false, required = false )
    /**
     * {@value async} Create environment asynchronously
     * {@code async = false}
     */
            boolean async = false;


    private final EnvironmentManager environmentManager;
    private final PeerManager peerManager;


    public BuildLocalEnvironmentCommand( final EnvironmentManager environmentManager, final PeerManager peerManager )
    {
        Preconditions.checkNotNull( environmentManager );
        Preconditions.checkNotNull( peerManager );

        this.environmentManager = environmentManager;
        this.peerManager = peerManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        String peerId = peerManager.getLocalPeer().getId();
        final Set<ResourceHost> resourceHosts = peerManager.getLocalPeer().getResourceHosts();

        if ( resourceHosts.size() < 1 )
        {
            System.out.println( "There are no resource hosts to build environment" );
            return null;
        }
        String hostId = resourceHosts.iterator().next().getId();
        NodeGroup nodeGroup = new NodeGroup( "NodeGroup1", templateName, ContainerSize.TINY, 1, 1, peerId, hostId );

        Topology topology = new Topology( "Dummy environment name", 1, 1 );
        topology.addNodeGroupPlacement( peerId, nodeGroup );

        Environment environment = environmentManager.createEnvironment( topology, async );

        System.out.println( String.format( "Environment created with id %s", environment.getId() ) );

        return null;
    }
}
