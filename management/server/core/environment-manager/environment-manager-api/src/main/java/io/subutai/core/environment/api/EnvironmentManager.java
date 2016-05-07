package io.subutai.core.environment.api;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.RolesAllowed;

import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.Topology;
import io.subutai.common.network.DomainLoadBalanceStrategy;
import io.subutai.common.peer.AlertHandler;
import io.subutai.common.peer.AlertHandlerPriority;
import io.subutai.common.peer.EnvironmentAlertHandlers;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.protocol.ReverseProxyConfig;
import io.subutai.common.security.SshEncryptionType;
import io.subutai.common.security.SshKeys;
import io.subutai.core.environment.api.ShareDto.ShareDto;
import io.subutai.core.environment.api.exception.EnvironmentCreationException;
import io.subutai.core.environment.api.exception.EnvironmentDestructionException;
import io.subutai.core.environment.api.exception.EnvironmentManagerException;


/**
 * Environment Manager
 */
public interface EnvironmentManager
{

    /**
     * Returns all existing environments
     *
     * @return - set of {@code Environment}
     */
    Set<Environment> getEnvironments();

    Set<Environment> getEnvironmentsByOwnerId( long userId );

    /**
     * Creates environment based on a passed topology
     *
     * @param topology - {@code Topology}
     * @param async - indicates whether environment is created synchronously or asynchronously to the calling party
     *
     * @return - created environment
     *
     * @throws EnvironmentCreationException - thrown if error occurs during environment creation
     */
    @RolesAllowed( "Environment-Management|Write" )
    Environment createEnvironment( Topology topology, boolean async ) throws EnvironmentCreationException;

    @RolesAllowed( "Environment-Management|Write" )
    UUID createEnvironmentAndGetTrackerID( Topology topology, boolean async ) throws EnvironmentCreationException;


    /**
     * Grows environment based on a passed topology
     *
     * @param topology - {@code Topology}
     * @param async - indicates whether environment is grown synchronously or asynchronously to the calling party
     *
     * @return - set of newly created {@code ContainerHost} or empty set if operation is async
     *
     * @throws EnvironmentModificationException - thrown if error occurs during environment modification
     * @throws EnvironmentNotFoundException - thrown if environment not found
     */
    Set<EnvironmentContainerHost> growEnvironment( String environmentId, Topology topology, boolean async )
            throws EnvironmentModificationException, EnvironmentNotFoundException;


    @RolesAllowed( "Environment-Management|Write" )
    UUID modifyEnvironmentAndGetTrackerID( String environmentId, Topology topology, List<String> removedContainers,
                                           boolean async )
            throws EnvironmentModificationException, EnvironmentNotFoundException;

    /**
     * Assigns ssh key to environment and inserts it into authorized_keys file of all the containers within the
     * environment
     *
     * @param environmentId - environment id
     * @param sshKey - ssh key content
     * @param async - indicates whether ssh key is added synchronously or asynchronously to the calling party
     */
    void addSshKey( String environmentId, String sshKey, boolean async )
            throws EnvironmentNotFoundException, EnvironmentModificationException;

    /**
     * Removes ssh key from environment containers authorized_keys file
     *
     * @param environmentId - environment id
     * @param sshKey - ssh key content
     * @param async - indicates whether ssh key is removed synchronously or asynchronously to the calling party
     */
    void removeSshKey( String environmentId, String sshKey, boolean async )
            throws EnvironmentNotFoundException, EnvironmentModificationException;

    /**
     * Returns ssh keys of environment containers
     *
     * @param environmentId environment id
     * @param encType encription type
     *
     * @return ssh keys
     */
    SshKeys getSshKeys( String environmentId, SshEncryptionType encType );


    /**
     * Generates ssh key with given encryption type
     *
     * @param environmentId environment id
     * @param encType rsa or dsa
     *
     * @return ssh public key
     */
    SshKeys createSshKey( String environmentId, String hostname, SshEncryptionType encType );

    /**
     * Allows to change p2p network's secret key
     *
     * @param environmentId - environment id
     * @param newP2pSecretKey - new secret key
     * @param p2pSecretKeyTtlSec - new secret key's time-to-live in seconds
     */
    void resetP2PSecretKey( String environmentId, String newP2pSecretKey, long p2pSecretKeyTtlSec, boolean async )
            throws EnvironmentNotFoundException, EnvironmentModificationException;

    /**
     * Destroys environment by id.
     *
     * @param environmentId - environment id
     * @param async - indicates whether environment is destroyed synchronously or asynchronously to the calling party
     * containers were destroyed, otherwise an exception is thrown when first error occurs
     *
     * @throws EnvironmentDestructionException - thrown if error occurs during environment destruction
     * @throws EnvironmentNotFoundException - thrown if environment not found
     */
    void destroyEnvironment( String environmentId, boolean async )
            throws EnvironmentDestructionException, EnvironmentNotFoundException;


    /**
     * Destroys container. If this is the last container, the associated environment will be removed too
     *
     * @param environmentId - id of container environment
     * @param containerId - id of container to destroy
     * @param async - indicates whether container is destroyed synchronously or asynchronously to the calling party was
     * not destroyed due to some error, otherwise an exception is thrown
     *
     * @throws EnvironmentModificationException - thrown if error occurs during environment modification
     * @throws EnvironmentNotFoundException - thrown if environment not found
     */
    void destroyContainer( String environmentId, String containerId, boolean async )
            throws EnvironmentModificationException, EnvironmentNotFoundException;

    /**
     * Cancels active workflow for the specified environment
     *
     * @param environmentId id of environment
     *
     * @throws EnvironmentManagerException if exception is thrown during cancellation
     */
    void cancelEnvironmentWorkflow( final String environmentId ) throws EnvironmentManagerException;

    Map<String, CancellableWorkflow> getActiveWorkflows();

    /**
     * Returns environment by id
     *
     * @param environmentId - environment id
     *
     * @return - {@code Environment}
     *
     * @throws EnvironmentNotFoundException - thrown if environment not found
     */
    Environment loadEnvironment( String environmentId ) throws EnvironmentNotFoundException;


    /**
     * Get default domain name defaultDomainName: intra.lan
     *
     * @return - default domain name
     */
    String getDefaultDomainName();


    /**
     * Removes an assigned domain if any from the environment
     *
     * @param environmentId - id of the environment which domain to remove
     */
    void removeEnvironmentDomain( String environmentId )
            throws EnvironmentModificationException, EnvironmentNotFoundException;

    /**
     * Assigns a domain to the environment. External client would be able to access the environment containers via the
     * domain name.
     *
     * @param environmentId - id of the environment to assign the passed domain to
     * @param newDomain - domain url
     * @param domainLoadBalanceStrategy - strategy to load balance requests to the domain
     * @param sslCertPath - path to SSL certificate to enable HTTPS access to domain only, null if not needed
     */
    void assignEnvironmentDomain( String environmentId, String newDomain,
                                  DomainLoadBalanceStrategy domainLoadBalanceStrategy, String sslCertPath )
            throws EnvironmentModificationException, EnvironmentNotFoundException;

    /**
     * Returns the currently assigned domain
     *
     * @param environmentId - id of the environment which domain to return
     *
     * @return - domain url or null if not assigned
     */
    String getEnvironmentDomain( String environmentId )
            throws EnvironmentManagerException, EnvironmentNotFoundException;


    boolean isContainerInEnvironmentDomain( String containerHostId, String environmentId )
            throws EnvironmentManagerException, EnvironmentNotFoundException;


    void addContainerToEnvironmentDomain( String containerHostId, String environmentId )
            throws EnvironmentModificationException, EnvironmentNotFoundException, ContainerHostNotFoundException;

    /**
     * Sets up ssh connectivity for container. Clients can connect to the container via ssh during 30 seconds after this
     * call. Connection will remain active unless client is idle for 30 seconds.
     *
     * @param containerHostId container id
     * @param environmentId env id
     *
     * @return port for ssh connection
     */
    int setupSshTunnelForContainer( String containerHostId, String environmentId )
            throws EnvironmentModificationException, EnvironmentNotFoundException, ContainerHostNotFoundException;

    void removeContainerFromEnvironmentDomain( String containerHostId, String environmentId )
            throws EnvironmentModificationException, EnvironmentNotFoundException, ContainerHostNotFoundException;

    void notifyOnContainerDestroyed( Environment environment, String containerId );

    void addAlertHandler( AlertHandler alertHandler );

    void removeAlertHandler( AlertHandler alertHandler );

    Collection<AlertHandler> getRegisteredAlertHandlers();

    EnvironmentAlertHandlers getEnvironmentAlertHandlers( EnvironmentId environmentId )
            throws EnvironmentNotFoundException;


    List<ShareDto> getSharedUsers( String objectId ) throws EnvironmentNotFoundException;

    void shareEnvironment( ShareDto[] shareDto, String environmentId );


    void startMonitoring( String handlerId, AlertHandlerPriority handlerPriority, String environmentId )
            throws EnvironmentManagerException;

    void stopMonitoring( String handlerId, AlertHandlerPriority handlerPriority, String environmentId )
            throws EnvironmentManagerException;

    void addReverseProxy( final Environment environment, final ReverseProxyConfig reverseProxyConfig )
            throws EnvironmentModificationException;
}
