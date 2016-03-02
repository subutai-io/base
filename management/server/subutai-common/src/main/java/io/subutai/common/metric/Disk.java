package io.subutai.common.metric;


import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.Expose;


public class Disk
{
    @Expose
    @JsonProperty( "total" )
    Double total = 0.0;
    @Expose
    @JsonProperty( "used" )
    Double used = 0.0;


    public Disk( @JsonProperty( "total" ) final Double total, @JsonProperty( "used" ) final Double used )
    {
        this.total = total;
        this.used = used;
    }


    public Double getTotal()
    {
        return total;
    }


    public void setTotal( final Double total )
    {
        this.total = total;
    }


    public Double getUsed()
    {
        return used;
    }


    public void setUsed( final Double used )
    {
        this.used = used;
    }


    @JsonIgnore
    public Double getAvailableSpace()
    {
        return total - used;
    }


    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer( "Disk{" );
        sb.append( "total=" ).append( total );
        sb.append( ", used=" ).append( used );
        sb.append( '}' );
        return sb.toString();
    }
}