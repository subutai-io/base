package io.subutai.core.environment.impl.xpeer;


import java.util.Set;

import com.google.common.collect.Sets;

import io.subutai.common.environment.ContainerDto;
import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.network.NetworkResource;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.settings.Common;
import io.subutai.core.environment.impl.entity.LocalEnvironment;


public class RemoteEnvironment extends LocalEnvironment
{

    private String initiatorPeerId;
    private Set<ContainerHost> containers;


    public RemoteEnvironment( final NetworkResource networkResource, final String name,
                              final Set<ContainerHost> containers )
    {
        this.environmentId = networkResource.getEnvironmentId();
        this.containers = containers;
        this.initiatorPeerId = networkResource.getInitiatorPeerId();
        setP2PSubnet( networkResource.getP2pSubnet() );
        setVni( networkResource.getVni() );
        setStatus( EnvironmentStatus.UNKNOWN );
        setName( name );
    }


    public String getInitiatorPeerId()
    {
        return initiatorPeerId;
    }


    public Set<ContainerDto> getContainerDtos()
    {
        Set<ContainerDto> containerDtos = Sets.newHashSet();


        for ( ContainerHost host : containers )
        {
            containerDtos.add( new ContainerDto( host.getId(), getId(), host.getHostname(), host.getIp(),
                    host.getTemplateName(), host.getContainerSize(), host.getArch().name(), Sets.<String>newHashSet(),
                    host.getPeerId(), host.getResourceHostId().getId(), true,
                    Common.HUB_ID.equals( initiatorPeerId ) ? Common.HUB_ID : Common.SUBUTAI_ID, host.getState(),
                    host.getTemplateId(), host.getContainerName(), host.getResourceHostId().getId() ) );
        }


        return containerDtos;
    }
}
