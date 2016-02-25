package io.subutai.common.environment;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import io.subutai.common.gson.required.GsonRequired;
import io.subutai.common.protocol.PlacementStrategy;
import io.subutai.common.util.CollectionUtil;


/**
 * Blueprint for environment creation stores nodeGroups.
 *
 * @see NodeGroup
 */
public class Blueprint
{
    @JsonIgnore
    private UUID id;
    @JsonProperty( "name" )
    @GsonRequired
    private String name;
    @JsonProperty( "sshKey" )
    private String sshKey;
    @JsonProperty( "nodegroups" )
    @GsonRequired
    private Set<NodeGroup> nodeGroups;
//    @JsonProperty( "strategyId" )
//    private String strategyId;
//    @JsonProperty( "containerDistributionType" )
//    private ContainerDistributionType distributionType = ContainerDistributionType.AUTO;


    public Blueprint( @JsonProperty( "name" ) final String name, @JsonProperty( "sshKey" ) final String sshKey,
                      @JsonProperty( "nodegroups" ) final Set<NodeGroup> nodeGroups/*, final String strategyId*/ )
    {
        this.name = name;
        this.nodeGroups = nodeGroups;
        this.sshKey = sshKey;
//        this.strategyId = strategyId;
    }


    public Blueprint( final String name, final Set<NodeGroup> nodeGroups )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( name ), "Invalid name" );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( nodeGroups ), "Invalid node group set" );

        this.id = UUID.randomUUID();
        this.name = name;
        this.nodeGroups = nodeGroups;
    }


    public UUID getId()
    {
        return id;
    }


    public void setId( final UUID id )
    {
        this.id = id;
    }


    public String getName()
    {
        return name;
    }


//    public String getStrategyId()
//    {
//        return strategyId;
//    }
//
//
//    public ContainerDistributionType getDistributionType()
//    {
//        return distributionType;
//    }


    public Set<NodeGroup> getNodeGroups()
    {
        return nodeGroups == null ? Sets.<NodeGroup>newHashSet() : Collections.unmodifiableSet( nodeGroups );
    }


    @JsonIgnore
    public Map<String, Set<NodeGroup>> getNodeGroupsMap()
    {
        Map<String, Set<NodeGroup>> result = new HashMap<>();

        for ( NodeGroup nodeGroup : nodeGroups )
        {
            String key = nodeGroup.getPeerId();
            Set<NodeGroup> nodes = result.get( key );
            if ( nodes == null )
            {
                nodes = new HashSet<>();
                result.put( key, nodes );
            }
            nodes.add( nodeGroup );
        }
        return result;
    }


    @JsonIgnore
    public Set<String> getPeers()
    {
        Set<String> result = new HashSet<>();

        for ( NodeGroup nodeGroup : nodeGroups )
        {
            result.add( nodeGroup.getPeerId() );
        }
        return result;
    }


    public String getSshKey()
    {
        return sshKey;
    }


    @JsonIgnore
    public boolean isDistributed()
    {
        for ( NodeGroup nodeGroup : nodeGroups )
        {
            if ( nodeGroup.getPeerId() == null || nodeGroup.getHostId() == null )
            {
                return false;
            }
        }
        return true;
    }


    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer( "Blueprint{" );
        sb.append( "id=" ).append( id );
        sb.append( ", name='" ).append( name ).append( '\'' );
        sb.append( ", sshKey='" ).append( sshKey ).append( '\'' );
        sb.append( ", nodeGroups=" ).append( nodeGroups );
//        sb.append( ", distributionType=" ).append( distributionType );
        sb.append( '}' );
        return sb.toString();
    }
}
