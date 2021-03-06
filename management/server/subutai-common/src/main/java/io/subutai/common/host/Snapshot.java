package io.subutai.common.host;


import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;


public class Snapshot
{
    @JsonProperty( "containerName" )
    private final String containerName;
    @JsonProperty( "partition" )
    private final String partition;
    @JsonProperty( "label" )
    private final String label;
    @JsonProperty( "created" )
    private final Date created;
    @JsonProperty( "createdTimestamp" )
    private final long createdTimestamp;


    public Snapshot( @JsonProperty( "containerName" ) final String containerName,
                     @JsonProperty( "partition" ) final String partition, @JsonProperty( "label" ) final String label,
                     @JsonProperty( "created" ) final Date created )
    {
        this.containerName = containerName;
        this.partition = partition;
        this.label = label;
        this.created = created;
        this.createdTimestamp = created.getTime();
    }


    /**
     * Returns partition where snapshot is taken (home|opt|var|rootfs) If partition is equal to name of host container,
     * then the snapshot is of parent dataset i.e. if container is named foo, and partition is equal to "foo" then this
     * is a snapshot of parent dataset ex: foo@snap and "foo" is returned otherwise this is a container partition
     * snapshot ex: foo/home@snap and "home" is returned
     *
     * @return name of partition
     */
    public String getPartition()
    {
        return partition;
    }


    /**
     * Returns name of snapshot (label), e.g. if snapshot is foo/rootfs@snap then "snap" is returned
     *
     * @return name of snapshot
     */
    public String getLabel()
    {
        return label;
    }


    public String getContainerName()
    {
        return containerName;
    }


    public Date getCreated()
    {
        return created;
    }


    public long getCreatedTimestamp()
    {
        return createdTimestamp;
    }


    @Override
    public String toString()
    {
        return "Snapshot{" + "containerName='" + containerName + '\'' + ", partition='" + partition + '\'' + ", label='"
                + label + '\'' + ", created=" + created + ", createdTimestamp=" + createdTimestamp + '}';
    }
}
