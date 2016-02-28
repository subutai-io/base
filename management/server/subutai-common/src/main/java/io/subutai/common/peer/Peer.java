package io.subutai.common.peer;


import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.openpgp.PGPPublicKeyRing;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.CreateEnvironmentContainerGroupRequest;
import io.subutai.common.environment.CreateEnvironmentContainerGroupResponse;
import io.subutai.common.environment.PrepareTemplatesRequest;
import io.subutai.common.environment.PrepareTemplatesResponse;
import io.subutai.common.host.ContainerHostInfo;
import io.subutai.common.host.ContainerHostInfoModel;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.HostId;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.metric.ProcessResourceUsage;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.network.Gateway;
import io.subutai.common.network.Gateways;
import io.subutai.common.network.Vni;
import io.subutai.common.network.Vnis;
import io.subutai.common.protocol.ControlNetworkConfig;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.protocol.PingDistances;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.resource.HistoricalMetrics;
import io.subutai.common.resource.PeerResources;
import io.subutai.common.security.PublicKeyContainer;


/**
 * Peer interface
 */
public interface Peer
{

    /**
     * Returns id of peer
     */
    public String getId();

    /**
     * Returns name of peer
     */
    public String getName();

    /**
     * Returns owner id of peer
     */
    public String getOwnerId();

    /**
     * Returns metadata object of peer
     */
    public PeerInfo getPeerInfo() throws PeerException;

    /**
     * Creates environment container group on the peer
     *
     * @param request - container creation request
     */
    public CreateEnvironmentContainerGroupResponse createEnvironmentContainerGroup(
            final CreateEnvironmentContainerGroupRequest request ) throws PeerException;


    /**
     * Start container on the peer
     */
    public void startContainer( ContainerId containerId ) throws PeerException;

    /**
     * Stops container on the peer
     */
    public void stopContainer( ContainerId containerId ) throws PeerException;

    /**
     * Destroys container on the peer
     */
    public void destroyContainer( ContainerId containerId ) throws PeerException;

    /**
     * Sets default gateway for the container
     */
    public void setDefaultGateway( ContainerGateway containerGateway ) throws PeerException;

    /**
     * Cleans up environment networking settings. This method is called when an environment is being destroyed to clean
     * up its settings on the local peer.
     */
    void cleanupEnvironmentNetworkSettings( final EnvironmentId environmentId ) throws PeerException;

    /**
     * Returns true of the host is connected, false otherwise
     */
    public boolean isConnected( HostId hostId );

    /**
     * Executes command on the container
     *
     * @param requestBuilder - command
     * @param host - target host
     */
    public CommandResult execute( RequestBuilder requestBuilder, Host host ) throws CommandException;

    /**
     * Executes command on the container
     *
     * @param requestBuilder - command
     * @param host - target host
     * @param callback - callback to trigger on each response chunk to the command
     */
    public CommandResult execute( RequestBuilder requestBuilder, Host host, CommandCallback callback )
            throws CommandException;

    /**
     * Executes command on the container asynchronously
     *
     * @param requestBuilder - command
     * @param host - target host
     * @param callback - callback to trigger on each response chunk to the command
     */
    public void executeAsync( final RequestBuilder requestBuilder, final Host host, final CommandCallback callback )
            throws CommandException;

    /**
     * Executes command on the container asynchronously
     *
     * @param requestBuilder - command
     * @param host - target host
     */
    public void executeAsync( final RequestBuilder requestBuilder, final Host host ) throws CommandException;

    /**
     * Returns true if this a local peer, false otherwise
     */
    public boolean isLocal();


    /**
     * Returns template by name
     */
    public TemplateKurjun getTemplate( String templateName ) throws PeerException;

    /**
     * Returns true of the peer is reachable online, false otherwise
     */
    public boolean isOnline();

    /**
     * Sends message to the peer
     *
     * @param request - message
     * @param recipient - recipient
     * @param requestTimeout - message timeout
     * @param responseType -  type of response to return
     * @param responseTimeout - response timeout
     * @param headers - map of http headers to pass with message
     *
     * @return - response from the recipient
     */
    public <T, V> V sendRequest( T request, String recipient, int requestTimeout, Class<V> responseType,
                                 int responseTimeout, Map<String, String> headers ) throws PeerException;

    /**
     * Sends message to the peer
     *
     * @param request - message
     * @param recipient - recipient
     * @param requestTimeout - message timeout
     * @param headers - map of http headers to pass with message
     */
    public <T> void sendRequest( T request, String recipient, int requestTimeout, Map<String, String> headers )
            throws PeerException;

    /**
     * Returns state of container
     */
    public ContainerHostState getContainerState( ContainerId containerId ) throws PeerException;

    /**
     * Returns set of container information of the environment
     */
    public Set<ContainerHostInfo> getEnvironmentContainers( EnvironmentId environmentId ) throws PeerException;

    //******** Quota functions ***********

    /**
     * Returns resource usage of process on container by its PID
     *
     * @param containerId - target container
     * @param pid - pid of process
     */
    public ProcessResourceUsage getProcessResourceUsage( final ContainerId containerId, int pid ) throws PeerException;

    /**
     * Returns allowed cpus/cores ids on container
     *
     * @param host - container
     *
     * @return - allowed cpu set
     */
    public Set<Integer> getCpuSet( ContainerHost host ) throws PeerException;

    /**
     * Sets allowed cpus/cores on container
     *
     * @param host - container
     * @param cpuSet - allowed cpu set
     */
    public void setCpuSet( ContainerHost host, Set<Integer> cpuSet ) throws PeerException;


    /**
     * Destroys container group
     *
     * @param environmentId - id fo environment
     *
     * @return {@code ContainersDestructionResult}
     */
    public ContainersDestructionResult destroyContainersByEnvironment( final String environmentId )
            throws PeerException;

    //networking


    /**
     * Sets up tunnels on the local peer to the specified remote peers
     */
    public int setupTunnels( Map<String, String> peerIps, String environmentId ) throws PeerException;

    /* ************************************************
     * Returns all existing gateways of the peer
     */
    public Gateways getGateways() throws PeerException;


    /* ************************************************
     * Reserves VNI on the peer
     */
    public Vni reserveVni( Vni vni ) throws PeerException;


    /* ************************************************
     * Returns all reserved vnis on the peer
     */
    public Vnis getReservedVnis() throws PeerException;

    /**
     * Gets containerHost by Id specified
     *
     * @return - containerHost
     */
    public HostInfo getContainerHostInfoById( String containerHostId ) throws PeerException;


    /* **************************************************************
     *
     */
    public PublicKeyContainer createPeerEnvironmentKeyPair( EnvironmentId environmentId/*, String userToken*/ )
            throws PeerException;

    void updatePeerEnvironmentPubKey( EnvironmentId environmentId, PGPPublicKeyRing publicKeyRing )
            throws PeerException;


    /**
     * Gets network interfaces
     */

    HostInterfaces getInterfaces() throws PeerException;

    /**
     * Resets a secret key for a given P2P network
     *
     * @param p2PCredentials - P2P network credentials
     */
    void resetP2PSecretKey( P2PCredentials p2PCredentials ) throws PeerException;


    void setupP2PConnection( P2PConfig config ) throws PeerException;

    void removeP2PConnection( EnvironmentId environmentId ) throws PeerException;

    void removePeerEnvironmentKeyPair( EnvironmentId environmentId ) throws PeerException;

    ResourceHostMetrics getResourceHostMetrics() throws PeerException;

    PeerResources getResourceLimits( String peerId ) throws PeerException;

    ContainerQuota getAvailableQuota( ContainerId containerId ) throws PeerException;

    ContainerQuota getQuota( ContainerId containerId ) throws PeerException;

    void setQuota( ContainerId containerId, ContainerQuota quota ) throws PeerException;


    void alert( AlertEvent alert ) throws PeerException;

    HistoricalMetrics getHistoricalMetrics( String hostName, Date startTime, Date endTime ) throws PeerException;

    ControlNetworkConfig getControlNetworkConfig( String localPeerId ) throws PeerException;

    boolean updateControlNetworkConfig( ControlNetworkConfig config ) throws PeerException;

    PingDistances getCommunityDistances( String communityName, Integer maxAddress ) throws PeerException;

    void addPeerEnvironmentPubKey( String keyId, PGPPublicKeyRing pek );

    HostId getResourceHostIdByContainerId( ContainerId id ) throws PeerException;

    PrepareTemplatesResponse prepareTemplates( final PrepareTemplatesRequest request ) throws PeerException;
}
