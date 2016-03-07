package io.subutai.core.environment.impl.workflow.creation.steps.helpers;


import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.net.util.SubnetUtils;

import io.subutai.common.environment.CreateEnvironmentContainerGroupRequest;
import io.subutai.common.environment.CreateEnvironmentContainerResponseCollector;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.Node;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.Peer;
import io.subutai.common.task.CloneRequest;


public class CreatePeerNodeGroupsTask implements Callable<CreateEnvironmentContainerResponseCollector>
{
    private static final Logger LOG = LoggerFactory.getLogger( CreatePeerNodeGroupsTask.class );

    private final Peer peer;
    private final Set<Node> nodes;
    private final LocalPeer localPeer;
    private final Environment environment;
    private final int ipAddressOffset;


    public CreatePeerNodeGroupsTask( final Peer peer, final LocalPeer localPeer, final Environment environment,
                                     final int ipAddressOffset, final Set<Node> nodes )
    {
        this.peer = peer;
        this.nodes = nodes;
        this.localPeer = localPeer;
        this.environment = environment;
        this.ipAddressOffset = ipAddressOffset;
    }


    @Override
    public CreateEnvironmentContainerResponseCollector call() throws Exception
    {
        SubnetUtils.SubnetInfo subnetInfo = new SubnetUtils( environment.getSubnetCidr() ).getInfo();
        String maskLength = subnetInfo.getCidrSignature().split( "/" )[1];

        final CreateEnvironmentContainerGroupRequest request = new CreateEnvironmentContainerGroupRequest();
        try
        {
            int currentIpAddressOffset = 0;
            for ( Node node : nodes )
            {
                LOG.debug( String.format( "Scheduling on %s %s", node.getPeerId(), node.getName() ) );

                final String ip = subnetInfo.getAllAddresses()[( ipAddressOffset + currentIpAddressOffset )];
                ContainerSize size = node.getType();
                CloneRequest cloneRequest =
                        new CloneRequest( node.getHostId(), node.getHostname(), node.getName(), ip + "/" + maskLength,
                                environment.getId(), localPeer.getId(), localPeer.getOwnerId(), node.getTemplateName(),
                                HostArchitecture.AMD64, size );

                request.addRequest( cloneRequest );

                currentIpAddressOffset++;
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage(), e );
        }
        return peer.createEnvironmentContainerGroup( request );
    }
}
