package io.subutai.core.hostregistry.api;


import java.util.Set;

import io.subutai.common.host.ContainerHostInfo;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.ResourceHostInfo;


/**
 * Stores currently connected hosts
 */
public interface HostRegistry
{

    /**
     * Updates timestamp of resource host cache entry so that it does not get evicted
     *
     * @param resourceHostId - id of resource host
     */
    public void updateResourceHostEntryTimestamp( String resourceHostId );

    /**
     * Returns container host info by id
     *
     * @param id - id of container
     *
     * @return - container host info
     *
     * @throws HostDisconnectedException - thrown when container host is not present on any of connected resource hosts.
     * However if it is present but status is other then RUNNING this exception is not thrown
     */
    public ContainerHostInfo getContainerHostInfoById( String id ) throws HostDisconnectedException;

    /**
     * Returns container host info by name
     *
     * @param hostname - name of container
     *
     * @return - container host info
     *
     * @throws HostDisconnectedException - thrown when container host is not present on any of connected resource hosts.
     * However if it is present but status is other then RUNNING this exception is not thrown
     */
    public ContainerHostInfo getContainerHostInfoByHostname( String hostname ) throws HostDisconnectedException;


    /**
     * Returns all present container hosts info on all connected resource hosts
     */
    public Set<ContainerHostInfo> getContainerHostsInfo();

    /**
     * Returns resource host info by id
     *
     * @param id - id of resource host
     *
     * @return - resource host
     *
     * @throws HostDisconnectedException - thrown if resource host is not connected
     */
    public ResourceHostInfo getResourceHostInfoById( String id ) throws HostDisconnectedException;


    /**
     * Returns resource host info by name
     *
     * @param hostname - name of resource host
     *
     * @return - resource host
     *
     * @throws HostDisconnectedException - thrown if resource host is not connected
     */
    public ResourceHostInfo getResourceHostInfoByHostname( String hostname ) throws HostDisconnectedException;

    /**
     * Returns all currently connected resource hosts info
     */
    public Set<ResourceHostInfo> getResourceHostsInfo();

    /**
     * Adds host heartbeat listener
     */
    public void addHostListener( HostListener listener );

    /**
     * Removes host heartbeat listener
     */
    public void removeHostListener( HostListener listener );

    /**
     * Returns resource host info by its hosted container host info
     *
     * @param containerHostInfo - container host info
     *
     * @return - resource host info
     *
     * @throws HostDisconnectedException - thrown if resource host is not connected
     */
    public ResourceHostInfo getResourceHostByContainerHost( ContainerHostInfo containerHostInfo )
            throws HostDisconnectedException;

    /**
     * Returns host info by id. Host info might be a resource host info or container host info
     *
     * @param hostId - id of host
     *
     * @return - {@code HostInfo}
     *
     * @throws HostDisconnectedException - thrown if container host is not present or resource host is not connected
     * (depending if this is a container or resource host info)
     */
    public HostInfo getHostInfoById( String hostId ) throws HostDisconnectedException;
}
