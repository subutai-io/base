package io.subutai.core.strategy.api;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import io.subutai.common.environment.Node;
import io.subutai.common.util.CollectionUtil;


/**
 * Blueprint for environment creation stores nodeGroups.
 *
 * @see Node
 */
public class Blueprint
{
    @JsonIgnore
    private UUID id;
    @JsonProperty( "name" )
    private String name;

    @JsonProperty( "nodes" )
    private List<NodeSchema> nodes;

    @JsonProperty( "sshGroupId" )
    private int sshGroupId;

    @JsonProperty( "hostGroupId" )
    private int hostGroupId;


    public Blueprint( @JsonProperty( "name" ) final String name, @JsonProperty( "sshGroupId" ) final int sshGroupId,
                      @JsonProperty( "hostGroupId" ) final int hostGroupId,
                      @JsonProperty( "nodes" ) List<NodeSchema> nodes )
    {

        Preconditions.checkArgument( !Strings.isNullOrEmpty( name ), "Invalid name" );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( nodes ), "Invalid node group set" );

        this.id = UUID.randomUUID();
        this.name = name;
        this.sshGroupId = sshGroupId;
        this.hostGroupId = hostGroupId;
        this.nodes = nodes;
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


    public List<NodeSchema> getNodes()
    {
        return nodes == null ? Lists.<NodeSchema>newArrayList() : Collections.unmodifiableList( nodes );
    }


    public int getSshGroupId()
    {
        return sshGroupId;
    }


    public int getHostGroupId()
    {
        return hostGroupId;
    }
}
