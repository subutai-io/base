package io.subutai.core.localpeer.impl.entity;


import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.subutai.common.network.NetworkResource;
import io.subutai.common.settings.Common;
import io.subutai.common.util.IPUtil;
import io.subutai.common.util.NumUtil;


@Entity
@Table( name = "net_resource" )
@Access( AccessType.FIELD )
public class NetworkResourceEntity implements NetworkResource
{
    @Id
    @Column( name = "environment_id" )
    @JsonProperty( "environmentId" )
    protected String environmentId;

    @Column( name = "vni" )
    @JsonProperty( "vni" )
    private long vni;

    @Column( name = "vlan" )
    @JsonProperty( "vlan" )
    private int vlan;

    @Column( name = "p2p_subnet" )
    @JsonProperty( "p2pSubnet" )
    private String p2pSubnet;

    @Column( name = "container_subnet" )
    @JsonProperty( "containerSubnet" )
    private String containerSubnet;


    protected NetworkResourceEntity()
    {

    }


    public NetworkResourceEntity( NetworkResource networkResource, int vlan )
    {
        Preconditions.checkNotNull( networkResource );

        this.environmentId = networkResource.getEnvironmentId();
        this.vni = networkResource.getVni();
        this.p2pSubnet = IPUtil.getNetworkAddress( networkResource.getP2pSubnet() );
        this.containerSubnet = IPUtil.getNetworkAddress( networkResource.getContainerSubnet() );
        this.vlan = vlan;
    }


    public NetworkResourceEntity( @JsonProperty( "environmentId" ) final String environmentId,
                                  @JsonProperty( "vni" ) final long vni,
                                  @JsonProperty( "p2pSubnet" ) final String p2pSubnet,
                                  @JsonProperty( "containerSubnet" ) final String containerSubnet,
                                  @JsonProperty( "vlan" ) final int vlan )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( environmentId ) );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( p2pSubnet ) );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( containerSubnet ) );
        Preconditions.checkArgument( NumUtil.isLongBetween( vni, Common.MIN_VNI_ID, Common.MAX_VNI_ID ) );
        Preconditions.checkArgument( NumUtil.isIntBetween( vlan, Common.MIN_VLAN_ID, Common.MAX_VLAN_ID ) );

        this.environmentId = environmentId;
        this.vni = vni;
        this.p2pSubnet = p2pSubnet;
        this.containerSubnet = containerSubnet;
        this.vlan = vlan;
    }


    @Override
    public String getEnvironmentId()
    {
        return environmentId;
    }


    @Override
    public long getVni()
    {
        return vni;
    }


    @Override
    public String getP2pSubnet()
    {
        return p2pSubnet;
    }


    @Override
    public String getContainerSubnet()
    {
        return containerSubnet;
    }


    @Override
    public int getVlan()
    {
        return vlan;
    }


    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper( this ).add( "environmentId", environmentId ).add( "vni", vni )
                          .add( "vlan", vlan ).add( "p2pSubnet", p2pSubnet ).add( "containerSubnet", containerSubnet )
                          .toString();
    }
}
