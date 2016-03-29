package io.subutai.core.network.impl;


import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import io.subutai.common.command.RequestBuilder;
import io.subutai.common.network.DomainLoadBalanceStrategy;


/**
 * Networking commands
 */

public class Commands
{
    private static final String MANAGEMENT_HOST_NETWORK_BINDING = "subutai management_network";
    private static final String MANAGEMENT_PROXY_BINDING = "subutai proxy";


    public RequestBuilder getP2PConnectionsCommand()
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs( Lists.newArrayList( "p2p", "-p" ) );
    }


    public RequestBuilder getSetupP2PConnectionCommand( String interfaceName, String localIp, String p2pHash,
                                                        String secretKey, long secretKeyTtlSec )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs(
                Lists.newArrayList( "p2p", "-c", interfaceName, p2pHash, secretKey, String.valueOf( secretKeyTtlSec ),
                        Strings.isNullOrEmpty( localIp ) ? "" : localIp ) ).withTimeout( 90 );
    }


    public RequestBuilder getRemoveP2PConnectionCommand( String p2pHash )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING )
                .withCmdArgs( Lists.newArrayList( "p2p", "-d", p2pHash ) );
    }


    public RequestBuilder getResetP2PSecretKey( String p2pHash, String newSecretKey, long ttlSeconds )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING )
                .withCmdArgs( Lists.newArrayList( "p2p", "-u", p2pHash, newSecretKey, String.valueOf( ttlSeconds ) ) );
    }


    public RequestBuilder getSetupTunnelCommand( String tunnelName, String tunnelIp, String tunnelType )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING )
                .withCmdArgs( Lists.newArrayList( "-c", tunnelName, tunnelIp, tunnelType ) );
    }


    public RequestBuilder getRemoveTunnelCommand( String tunnelName )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING )
                .withCmdArgs( Lists.newArrayList( "-r", tunnelName ) );
    }


    public RequestBuilder getListTunnelsCommand()
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs( Lists.newArrayList( "-l" ) );
    }


    public RequestBuilder getSetupVniVlanMappingCommand( String tunnelName, long vni, int vLanId, String environmentId )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs(
                Lists.newArrayList( "-m", tunnelName, String.valueOf( vni ), String.valueOf( vLanId ),
                        environmentId ) );
    }


    public RequestBuilder getRemoveVniVlanMappingCommand( String tunnelName, long vni, int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING )
                .withCmdArgs( Lists.newArrayList( "-M", tunnelName, String.valueOf( vni ), String.valueOf( vLanId ) ) );
    }


    public RequestBuilder getListVniVlanMappingsCommand()
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs( Lists.newArrayList( "-v" ) );
    }


    public RequestBuilder getReserveVniCommand( long vni, int vlan, String environmentId )
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs(
                Lists.newArrayList( "-E", String.valueOf( vni ), String.valueOf( vlan ), environmentId ) );
    }


    public RequestBuilder getListReservedVnisCommand()
    {
        return new RequestBuilder( MANAGEMENT_HOST_NETWORK_BINDING ).withCmdArgs( Lists.newArrayList( "-Z", "list" ) );
    }


    public RequestBuilder getGetVlanDomainCommand( int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING )
                .withCmdArgs( Lists.newArrayList( "check", String.valueOf( vLanId ), "-d" ) );
    }


    public RequestBuilder getRemoveVlanDomainCommand( final int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING )
                .withCmdArgs( Lists.newArrayList( "del", String.valueOf( vLanId ), "-d" ) );
    }


    public RequestBuilder getSetVlanDomainCommand( final int vLanId, final String domain,
                                                   final DomainLoadBalanceStrategy domainLoadBalanceStrategy,
                                                   final String sslCertPath )
    {
        List<String> args = Lists.newArrayList( "add", String.valueOf( vLanId ), "-d", domain, "-p",
                domainLoadBalanceStrategy.getValue() );
        if ( !Strings.isNullOrEmpty( sslCertPath ) )
        {
            args.add( "-f" );
            args.add( sslCertPath );
        }
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING ).withCmdArgs( args );
    }


    public RequestBuilder getCheckIpInVlanDomainCommand( final String hostIp, final int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING )
                .withCmdArgs( Lists.newArrayList( "check", String.valueOf( vLanId ), "-h", hostIp ) );
    }


    public RequestBuilder getAddIpToVlanDomainCommand( final String hostIp, final int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING )
                .withCmdArgs( Lists.newArrayList( "add", String.valueOf( vLanId ), "-h", hostIp ) );
    }


    public RequestBuilder getRemoveIpFromVlanDomainCommand( final String hostIp, final int vLanId )
    {
        return new RequestBuilder( MANAGEMENT_PROXY_BINDING )
                .withCmdArgs( Lists.newArrayList( "del", String.valueOf( vLanId ), "-h", hostIp ) );
    }


    public RequestBuilder getSetupContainerSshCommand( final String containerIp, final int sshIdleTimeout )
    {
        return new RequestBuilder( String.format( "subutai tunnel %s %d", containerIp, sshIdleTimeout ) );
    }


    public RequestBuilder getPingDistanceCommand( final String ip )
    {
        return new RequestBuilder( "ping -c 10 -i 0.2 -w 3 " + ip );
    }
}
