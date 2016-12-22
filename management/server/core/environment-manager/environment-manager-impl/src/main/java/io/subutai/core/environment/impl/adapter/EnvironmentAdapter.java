package io.subutai.core.environment.impl.adapter;


import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.reflect.TypeToken;

import io.subutai.common.environment.EnvironmentPeer;
import io.subutai.common.environment.EnvironmentStatus;
import io.subutai.common.environment.RhP2pIp;
import io.subutai.common.exception.ActionFailedException;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.security.SshKey;
import io.subutai.common.security.SshKeys;
import io.subutai.common.settings.Common;
import io.subutai.common.util.P2PUtil;
import io.subutai.common.util.ServiceLocator;
import io.subutai.core.environment.impl.EnvironmentManagerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentContainerImpl;
import io.subutai.core.environment.impl.entity.LocalEnvironment;
import io.subutai.core.hubmanager.api.HubManager;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.hub.share.common.HubAdapter;
import io.subutai.hub.share.json.JsonUtil;


public class EnvironmentAdapter
{
    private final Logger log = LoggerFactory.getLogger( getClass() );

    private final EnvironmentManagerImpl environmentManager;

    private final ProxyContainerHelper proxyContainerHelper;

    private final HubAdapter hubAdapter;
    private final IdentityManager identityManager;


    public EnvironmentAdapter( EnvironmentManagerImpl environmentManager, PeerManager peerManager,
                               HubAdapter hubAdapter, IdentityManager identityManager )
    {
        this.environmentManager = environmentManager;

        proxyContainerHelper = new ProxyContainerHelper( peerManager );

        this.hubAdapter = hubAdapter;

        this.identityManager = identityManager;
    }


    public HubEnvironment get( String id )
    {
        try
        {
            for ( HubEnvironment e : getEnvironments( identityManager.isTenantManager() ) )
            {
                if ( e.getId().equals( id ) )
                {
                    return e;
                }
            }
        }
        catch ( ActionFailedException e )
        {
            log.error( e.getMessage() );
        }

        return null;
    }


    /**
     * Returns hub environments for this peer. Throws {@code ActionFailedException} if requests to Hub failed for some
     * reason
     *
     * @param all true: returns all environments, false: returns current user environments
     */
    public Set<HubEnvironment> getEnvironments( boolean all )
    {
        if ( !canWorkWithHub() )
        {
            throw new ActionFailedException( "Peer is not registered with Hub or connection to Hub failed" );
        }

        String json = all ? hubAdapter.getAllEnvironmentsForPeer() : hubAdapter.getUserEnvironmentsForPeer();

        if ( json == null )
        {
            throw new ActionFailedException( "Failed to obtain environments from Hub" );
        }

        log.debug( "Json with environments: {}", json );

        Set<HubEnvironment> envs = new HashSet<>();

        try
        {
            ArrayNode arr = JsonUtil.fromJson( json, ArrayNode.class );

            for ( int i = 0; i < arr.size(); i++ )
            {
                envs.add( new HubEnvironment( this, arr.get( i ), environmentManager, proxyContainerHelper ) );
            }
        }
        catch ( Exception e )
        {
            log.error( "Error to parse json: ", e );

            throw new ActionFailedException( "Failed to parse environments from Hub: " + e.getMessage() );
        }

        return envs;
    }


    public Set<String> getDeletedEnvironmentsIds()
    {

        if ( !canWorkWithHub() )
        {
            throw new ActionFailedException( "Peer is not registered with Hub or connection to Hub failed" );
        }

        String json = hubAdapter.getDeletedEnvironmentsForPeer();

        if ( json == null )
        {
            throw new ActionFailedException( "Failed to obtain deleted environments from Hub" );
        }

        log.debug( "Json with deleted environments: {}", json );

        try
        {
            return io.subutai.common.util.JsonUtil.fromJson( json, new TypeToken<Set<String>>()
            {
            }.getType() );
        }
        catch ( Exception e )
        {
            log.error( "Error to parse json: ", e );

            throw new ActionFailedException( "Failed to parse deleted environments from Hub: " + e.getMessage() );
        }
    }


    public void destroyContainer( HubEnvironment env, String containerId )
    {
        if ( !canWorkWithHub() )
        {
            return;
        }

        try
        {
            EnvironmentContainerHost ch = env.getContainerHostById( containerId );

            ( ( EnvironmentContainerImpl ) ch ).destroy( false );

            hubAdapter.destroyContainer( env.getId(), containerId );
        }
        catch ( Exception e )
        {
            log.error( "Error to destroy container: ", e );
        }
    }


    public boolean removeEnvironment( String envId )
    {
        if ( !canWorkWithHub() )
        {
            return false;
        }

        try
        {
            hubAdapter.removeEnvironment( envId );

            return true;
        }
        catch ( Exception e )
        {
            log.error( "Error to remove environment: ", e );
        }

        return false;
    }


    public boolean removeEnvironment( LocalEnvironment env )
    {
        return removeEnvironment( env.getId() );
    }


    public boolean canWorkWithHub()
    {
        return isHubReachable() && isRegisteredWithHub();
    }


    public boolean isHubReachable()
    {
        HubManager hubManager = ServiceLocator.getServiceOrNull( HubManager.class );

        return hubManager != null && hubManager.isHubReachable();
    }


    public boolean isRegisteredWithHub()
    {
        HubManager hubManager = ServiceLocator.getServiceOrNull( HubManager.class );

        return hubManager != null && hubManager.isRegisteredWithHub();
    }


    public void uploadEnvironment( LocalEnvironment env )
    {
        if ( !canWorkWithHub() )
        {
            return;
        }

        if ( env.getStatus() != EnvironmentStatus.HEALTHY )
        {
            return;
        }

        try
        {
            ObjectNode envJson = environmentToJson( env );

            environmentPeersToJson( env, envJson );

            environmentContainersToJson( env, envJson );

            hubAdapter.uploadEnvironment( envJson.toString() );
        }
        catch ( Exception e )
        {
            log.debug( "Error to post local environment to Hub: ", e );
        }
    }


    public boolean uploadPeerOwnerEnvironment( LocalEnvironment env )
    {
        if ( !canWorkWithHub() )
        {
            return false;
        }

        if ( env.getStatus() != EnvironmentStatus.HEALTHY )
        {
            return false;
        }

        try
        {
            ObjectNode envJson = environmentToJson( env );

            environmentPeersToJson( env, envJson );

            environmentContainersToJson( env, envJson );

            hubAdapter.uploadPeerOwnerEnvironment( envJson.toString() );

            return true;
        }
        catch ( Exception e )
        {
            log.debug( "Error to post local environment to Hub: ", e );
        }

        return false;
    }


    public void removeSshKey( String envId, String sshKey )
    {
        if ( !canWorkWithHub() )
        {
            return;
        }

        hubAdapter.removeSshKey( envId, sshKey );
    }


    public void addSshKey( String envId, String sshKey )
    {
        if ( !canWorkWithHub() )
        {
            return;
        }

        hubAdapter.addSshKey( envId, sshKey );
    }


    private void environmentContainersToJson( LocalEnvironment env, ObjectNode json ) throws PeerException
    {
        ArrayNode contNode = json.putArray( "containers" );

        for ( EnvironmentContainerHost ch : env.getContainerHosts() )
        {
            ObjectNode peerJson = JsonUtil.createNode( "id", ch.getId() );

            peerJson.put( "name", ch.getContainerName() );

            peerJson.put( "hostname", ch.getHostname() );

            peerJson.put( "state", ch.getState().toString() );

            peerJson.put( "template", ch.getTemplateName() );

            peerJson.put( "size", ch.getContainerSize().toString() );

            peerJson.put( "peerId", ch.getPeer().getId() );

            peerJson.put( "rhId", ch.getResourceHostId().getId() );

            String ip = ch.getHostInterfaces().getAll().iterator().next().getIp();

            peerJson.put( "ip", ip );


            ArrayNode sshKeys = peerJson.putArray( "sshkeys" );

            SshKeys chSshKeys = ch.getAuthorizedKeys();

            for ( SshKey sshKey : chSshKeys.getKeys() )
            {
                sshKeys.add( sshKey.getPublicKey() );
            }

            contNode.add( peerJson );
        }
    }


    private ObjectNode environmentToJson( LocalEnvironment env )
    {
        ObjectNode json = JsonUtil.createNode( "id", env.getEnvironmentId().getId() );

        json.put( "name", env.getName() );

        json.put( "status", env.getStatus().toString() );

        json.put( "p2pHash", P2PUtil.generateHash( env.getEnvironmentId().getId() ) );

        json.put( "p2pTtl", Common.DEFAULT_P2P_SECRET_KEY_TTL_SEC );

        json.put( "p2pKey", env.getP2pKey() );

        json.put( "vni", env.getVni() );

        return json;
    }


    private void environmentPeersToJson( LocalEnvironment env, ObjectNode json ) throws PeerException
    {
        ArrayNode peers = json.putArray( "peers" );

        for ( Peer peer : env.getPeers() )
        {
            ObjectNode peerJson = JsonUtil.createNode( "id", peer.getId() );

            peerJson.put( "online", peer.isOnline() );

            putPeerResourceHostsJson( peerJson, env.getEnvironmentPeer( peer.getId() ) );

            peers.add( peerJson );
        }
    }


    private void putPeerResourceHostsJson( ObjectNode peerJson, EnvironmentPeer environmentPeer )
    {
        ArrayNode rhs = peerJson.putArray( "resourceHosts" );

        for ( RhP2pIp rh : environmentPeer.getRhP2pIps() )
        {
            ObjectNode rhJson = JsonUtil.createNode( "id", rh.getRhId() );

            rhJson.put( "p2pIp", rh.getP2pIp() );

            rhs.add( rhJson );
        }
    }
}
