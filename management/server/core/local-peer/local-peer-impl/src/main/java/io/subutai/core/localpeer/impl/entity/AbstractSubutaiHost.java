package io.subutai.core.localpeer.impl.entity;


import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Transient;

import com.google.common.base.Preconditions;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.HostInterface;
import io.subutai.common.host.HostInterfaceModel;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.Peer;


/**
 * Base Subutai host class.
 */
@Entity
@Inheritance( strategy = InheritanceType.TABLE_PER_CLASS )
@Access( AccessType.FIELD )
public abstract class AbstractSubutaiHost implements Host
{
    @Id
    @Column( name = "host_id" )
    protected String hostId;

    @Column( name = "peer_id", nullable = false )
    protected String peerId;

    @Column( name = "host_name", nullable = false )
    protected String hostname;

    @Column( name = "arch" )
    @Enumerated( EnumType.STRING )
    private HostArchitecture hostArchitecture;

    @Transient
    protected HostInterfaces hostInterfaces = new HostInterfaces();

    @Transient
    protected volatile long lastHeartbeat = 0;


    @Transient
    private Peer peer;


    public void init()
    {
        // Empty method
    }


    protected AbstractSubutaiHost( final String peerId, final String hostId, final String hostname,
                                   HostArchitecture architecture, HostInterfaces hostInterfaces )
    {
        Preconditions.checkNotNull( peerId );
        Preconditions.checkNotNull( hostId );
        Preconditions.checkNotNull( hostname );
        Preconditions.checkNotNull( architecture );
        Preconditions.checkNotNull( hostInterfaces );

        this.peerId = peerId;
        this.hostId = hostId;
        this.hostname = hostname;
        this.hostArchitecture = architecture;
        this.hostInterfaces = hostInterfaces;
    }


    protected AbstractSubutaiHost( final String peerId, final HostInfo hostInfo )
    {
        Preconditions.checkNotNull( hostInfo, "Host information is null" );
        Preconditions.checkNotNull( peerId, "Peer ID is null" );

        this.peerId = peerId;
        this.hostId = hostInfo.getId();
        this.hostname = hostInfo.getHostname();
        this.hostArchitecture = hostInfo.getArch();
        this.hostInterfaces = hostInfo.getHostInterfaces();
    }


    protected AbstractSubutaiHost()
    {
    }


    @Override
    public Peer getPeer()
    {
        return peer;
    }


    public void setPeer( Peer peer )
    {
        this.peer = peer;
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder ) throws CommandException
    {
        return execute( requestBuilder, null );
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder, final CommandCallback callback )
            throws CommandException
    {
        return getPeer().execute( requestBuilder, this, callback );
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder ) throws CommandException
    {
        executeAsync( requestBuilder, null );
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder, final CommandCallback callback )
            throws CommandException
    {
        getPeer().executeAsync( requestBuilder, this, callback );
    }


    @Override
    public String getId()
    {
        return hostId;
    }


    @Override
    public String getPeerId()
    {
        return peerId;
    }


    @Override
    public String getHostname()
    {
        return hostname;
    }


    public boolean updateHostInfo( final HostInfo hostInfo )
    {
        Preconditions.checkNotNull( hostInfo );

        this.lastHeartbeat = System.currentTimeMillis();

        this.hostInterfaces = hostInfo.getHostInterfaces();

        this.hostname = hostInfo.getHostname();

        return false;
    }


    @Override
    public abstract boolean isConnected();


    public long getLastHeartbeat()
    {
        return lastHeartbeat;
    }


    @Override
    public HostInterfaces getHostInterfaces()
    {
        return hostInterfaces;
    }


    @Override
    public HostInterface getInterfaceByName( final String interfaceName )
    {
        return getHostInterfaces().findByName( interfaceName );
    }


    public void addInterface( HostInterfaceModel hostInterface )
    {
        Preconditions.checkNotNull( hostInterface, "HostInterface could not be null." );

        hostInterfaces.addHostInterface( hostInterface );
    }


    @Override
    public HostArchitecture getArch()
    {
        return this.hostArchitecture;
    }


    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        final AbstractSubutaiHost that = ( AbstractSubutaiHost ) o;

        return getId().equals( that.getId() );
    }


    @Override
    public int hashCode()
    {
        return hostId != null ? hostId.hashCode() : 0;
    }


    @Override
    public String toString()
    {
        return "SubutaiHost{" +
                "peerId=" + peerId +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }


    @Override
    public int compareTo( final HostInfo o )
    {
        if ( hostname != null && o != null )
        {
            return hostname.compareTo( o.getHostname() );
        }
        return -1;
    }
}
