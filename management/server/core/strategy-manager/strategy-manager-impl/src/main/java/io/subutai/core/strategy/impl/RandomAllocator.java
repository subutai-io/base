package io.subutai.core.strategy.impl;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.subutai.hub.share.quota.ContainerQuota;
import io.subutai.hub.share.quota.ContainerSize;
import io.subutai.hub.share.resource.HostResources;
import io.subutai.hub.share.resource.PeerResources;


/**
 * Random container allocator class
 */
public class RandomAllocator extends PeerResources
{
    private List<AllocatedContainer> containers = new ArrayList<>();


    public RandomAllocator( final PeerResources peerResources )
    {
        super( peerResources.getPeerId(), peerResources.getEnvironmentLimit(), peerResources.getContainerLimit(),
                peerResources.getNetworkLimit(), peerResources.getHostResources() );
    }


    public boolean allocate( final String containerName, final String templateId, final ContainerQuota quota )
    {
        final Collection<HostResources> preferredHosts = getPreferredHosts();

        if ( preferredHosts.isEmpty() )
        {
            return false;
        }

        AllocatedContainer container = new AllocatedContainer( containerName, templateId, quota, getPeerId(),
                getPreferredHosts().iterator().next().getHostId() );
        containers.add( container );

        return true;
    }


    public Collection<HostResources> getPreferredHosts()
    {
        List<HostResources> result = new ArrayList<>( getHostResources() );
        Collections.shuffle( result );
        return result;
    }


    public List<AllocatedContainer> getContainers()
    {
        return containers;
    }
}
