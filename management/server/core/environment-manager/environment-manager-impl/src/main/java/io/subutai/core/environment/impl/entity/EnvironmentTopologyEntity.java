package io.subutai.core.environment.impl.entity;


import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;


/**
 * Environment Blueprint class is basic wrapper over {@link io.subutai.common.environment.Blueprint} to simplify data storing functionality.
 * Where Blueprint is stored in {@link #getInfo()} BLob field
 * @see io.subutai.common.environment.Blueprint
 */
@Entity
@Table( name = "env_topology" )
@Access( AccessType.FIELD )
public class EnvironmentTopologyEntity
{
    @Id
    @Column( name = "id" )
    private String id;

    @Lob
    @Column( name = "info" )
    private String info;


    public String getId()
    {
        return id;
    }


    public void setId( final String id )
    {
        this.id = id;
    }


    public String getInfo()
    {
        return info;
    }


    public void setInfo( final String info )
    {
        this.info = info;
    }
}
