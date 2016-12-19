package io.subutai.core.hubmanager.impl.environment.state.build;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Maps;

import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.HostAddresses;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.Peer;
import io.subutai.common.security.SshKey;
import io.subutai.common.security.SshKeys;
import io.subutai.common.settings.Common;
import io.subutai.core.hubmanager.api.exception.HubManagerException;
import io.subutai.core.hubmanager.impl.environment.state.Context;
import io.subutai.core.hubmanager.impl.environment.state.StateHandler;
import io.subutai.core.hubmanager.api.RestResult;
import io.subutai.hub.share.dto.environment.EnvironmentDto;
import io.subutai.hub.share.dto.environment.EnvironmentNodeDto;
import io.subutai.hub.share.dto.environment.EnvironmentNodesDto;
import io.subutai.hub.share.dto.environment.EnvironmentPeerDto;
import io.subutai.hub.share.dto.environment.SSHKeyDto;


public class ConfigureContainerStateHandler extends StateHandler
{
    public ConfigureContainerStateHandler( Context ctx )
    {
        super( ctx, "Containers configuration" );
    }


    @Override
    protected Object doHandle( EnvironmentPeerDto peerDto ) throws HubManagerException
    {
        try
        {
            logStart();

            EnvironmentDto envDto =
                    ctx.restClient.getStrict( path( "/rest/v1/environments/%s", peerDto ), EnvironmentDto.class );

            peerDto = configureSsh( peerDto, envDto );

            configureHosts( envDto );

            logEnd();

            return peerDto;
        }
        catch ( Exception e )
        {
            throw new HubManagerException( e );
        }
    }


    @Override
    protected RestResult<Object> post( EnvironmentPeerDto peerDto, Object body )
    {
        return ctx.restClient.post( path( "/rest/v1/environments/%s/container", peerDto ), body );
    }


    private EnvironmentPeerDto configureSsh( EnvironmentPeerDto peerDto, EnvironmentDto envDto )
            throws HubManagerException
    {
        try
        {
            EnvironmentId envId = new EnvironmentId( envDto.getId() );

            Environment environment = null;

            try
            {
                environment = ctx.envManager.loadEnvironment( envDto.getId() );
            }
            catch ( EnvironmentNotFoundException e )
            {
                log.info( e.getMessage() );
            }

            boolean isSsEnv = environment != null && !Common.HUB_ID.equals( environment.getPeerId() );


            Set<String> peerSshKeys = getCurrentSshKeys( envId, isSsEnv );

            Set<String> hubSshKeys = new HashSet<>();

            for ( EnvironmentNodesDto nodesDto : envDto.getNodes() )
            {
                for ( EnvironmentNodeDto nodeDto : nodesDto.getNodes() )
                {
                    if ( nodeDto.getSshKeys() != null )
                    {
                        hubSshKeys.addAll( trim( nodeDto.getSshKeys() ) );
                    }
                }
            }

            //remove obsolete keys
            Set<String> obsoleteKeys = new HashSet<>();

            obsoleteKeys.addAll( peerSshKeys );

            obsoleteKeys.removeAll( hubSshKeys );

            removeKeys( envId, obsoleteKeys, isSsEnv );

            //add new keys
            Set<String> newKeys = new HashSet<>();

            newKeys.addAll( hubSshKeys );

            newKeys.removeAll( peerSshKeys );

            if ( newKeys.isEmpty() )
            {
                return peerDto;
            }

            final SshKeys sshKeys = new SshKeys();

            sshKeys.addStringKeys( newKeys );

            if ( isSsEnv )
            {
                Set<Peer> peers = environment.getPeers();

                for ( final Peer peer : peers )
                {
                    if ( peer.isOnline() )
                    {
                        peer.configureSshInEnvironment( environment.getEnvironmentId(), sshKeys );

                        //add peer to dto
                        for ( SSHKeyDto sshKeyDto : peerDto.getEnvironmentInfo().getSshKeys() )
                        {
                            sshKeyDto.addConfiguredPeer( peer.getId() );
                        }
                    }
                }

                for ( SshKey sshKey : sshKeys.getKeys() )
                {
                    ctx.envManager.addSshKeyToEnvironmentEntity( environment.getId(), sshKey.getPublicKey() );
                }
            }
            else
            {
                ctx.localPeer.configureSshInEnvironment( envId, sshKeys );

                //add peer to dto
                for ( SSHKeyDto sshKeyDto : peerDto.getEnvironmentInfo().getSshKeys() )
                {
                    sshKeyDto.addConfiguredPeer( ctx.localPeer.getId() );
                }
            }

            return peerDto;
        }
        catch ( Exception e )
        {
            throw new HubManagerException( e );
        }
    }


    private Set<String> trim( final Set<String> sshKeys )
    {
        Set<String> trimmed = new HashSet<>();

        if ( sshKeys != null && !sshKeys.isEmpty() )
        {
            for ( String sshKey : sshKeys )
            {
                trimmed.add( sshKey.trim() );
            }
        }

        return trimmed;
    }


    private void removeKeys( EnvironmentId envId, Set<String> obsoleteKeys, boolean isSsEnv )
    {
        try
        {
            for ( String obsoleteKey : obsoleteKeys )
            {
                if ( isSsEnv )
                {
                    ctx.envManager.removeSshKey( envId.getId(), obsoleteKey, false );
                }
                else
                {
                    ctx.localPeer.removeFromAuthorizedKeys( envId, obsoleteKey );
                }
            }
        }
        catch ( Exception e )
        {
            log.error( "Error removing ssh key: {}", e.getMessage() );
        }
    }


    private Set<String> getCurrentSshKeys( EnvironmentId envId, boolean isSsEnv )
    {
        Set<String> currentKeys = new HashSet<>();

        try
        {
            Set<ContainerHost> containers = new HashSet<>();

            if ( isSsEnv )
            {
                Environment environment = ctx.envManager.loadEnvironment( envId.getId() );

                containers.addAll( environment.getContainerHosts() );
            }
            else
            {
                containers.addAll( ctx.localPeer.findContainersByEnvironmentId( envId.getId() ) );
            }

            for ( ContainerHost containerHost : containers )
            {
                SshKeys sshKeys = containerHost.getPeer().getContainerAuthorizedKeys( containerHost.getContainerId() );

                for ( SshKey sshKey : sshKeys.getKeys() )
                {
                    currentKeys.add( sshKey.getPublicKey() );
                }
            }
        }
        catch ( Exception e )
        {
            log.error( "Error getting env ssh keys: {}", e.getMessage() );
        }

        return currentKeys;
    }


    private void configureHosts( EnvironmentDto envDto )
    {
        log.info( "Configuring hosts:" );

        // <hostname, IPs>
        final Map<String, String> hostAddresses = Maps.newHashMap();

        for ( EnvironmentNodesDto nodesDto : envDto.getNodes() )
        {
            for ( EnvironmentNodeDto nodeDto : nodesDto.getNodes() )
            {
                log.info( "- noteDto: containerId={}, containerName={}, hostname={}, state={}",
                        nodeDto.getContainerId(), nodeDto.getContainerName(), nodeDto.getHostName(),
                        nodeDto.getState() );

                // Remove network mask "/24" in IP
                String ip = StringUtils.substringBefore( nodeDto.getIp(), "/" );

                hostAddresses.put( nodeDto.getHostName(), ip );
            }
        }

        try
        {
            ctx.localPeer.configureHostsInEnvironment( new EnvironmentId( envDto.getId() ),
                    new HostAddresses( hostAddresses ) );
        }
        catch ( Exception e )
        {
            log.error( "Error configuring hosts: {}", e.getMessage() );
        }
    }
}