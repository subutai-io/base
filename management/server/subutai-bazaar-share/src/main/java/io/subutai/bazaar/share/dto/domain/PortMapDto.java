package io.subutai.bazaar.share.dto.domain;


import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties( ignoreUnknown = true )
public class PortMapDto
{
    public enum Protocol
    {
        TCP, UDP, HTTP, HTTPS;


        public boolean isHttpOrHttps()
        {
            return this == HTTP || this == HTTPS;
        }
    }


    public enum State
    {
        CREATING, RESERVED, USED, DESTROYING, ERROR, DELETED, RECREATE
    }


    private String containerSSId;

    private Protocol protocol;

    private int internalPort;

    private int externalPort;

    private String ipAddr;

    private State state;

    private String stateDetails;

    private String domain;

    private String sslCertPem;

    private boolean sslBackend;

    private String errorLog;

    private boolean isProxied;

    private boolean redirectHttpToHttps;

    private boolean isHttp2;

    private UUID correlationId;


    public PortMapDto()
    {

    }


    public String getErrorLog()
    {
        return errorLog;
    }


    public void setErrorLog( final String errorLog )
    {
        this.errorLog = errorLog;
    }


    public String getContainerSSId()
    {
        return containerSSId;
    }


    public void setContainerSSId( final String containerSSId )
    {
        this.containerSSId = containerSSId;
    }


    public Protocol getProtocol()
    {
        return protocol;
    }


    public void setProtocol( final Protocol protocol )
    {
        this.protocol = protocol;
    }


    public int getInternalPort()
    {
        return internalPort;
    }


    public void setInternalPort( final int internalPort )
    {
        this.internalPort = internalPort;
    }


    public int getExternalPort()
    {
        return externalPort;
    }


    public void setExternalPort( final int externalPort )
    {
        this.externalPort = externalPort;
    }


    public String getIpAddr()
    {
        return ipAddr;
    }


    public void setIpAddr( final String ipAddr )
    {
        this.ipAddr = ipAddr;
    }


    public State getState()
    {
        return state;
    }


    public void setState( final State state )
    {
        this.state = state;
    }


    public String getStateDetails()
    {
        return stateDetails;
    }


    public void setStateDetails( final String stateDetails )
    {
        this.stateDetails = stateDetails;
    }


    public String getDomain()
    {
        return domain;
    }


    public void setDomain( final String domain )
    {
        this.domain = domain;
    }


    public String getSslCertPem()
    {
        return sslCertPem;
    }


    public void setSslCertPem( final String sslCertPem )
    {
        this.sslCertPem = sslCertPem;
    }


    public boolean isSslBackend()
    {
        return sslBackend;
    }


    public void setSslBackend( final boolean sslBackend )
    {
        this.sslBackend = sslBackend;
    }


    public boolean isProxied()
    {
        return isProxied;
    }


    public void setProxied( final boolean proxied )
    {
        isProxied = proxied;
    }


    public boolean isRedirectHttpToHttps()
    {
        return redirectHttpToHttps;
    }


    public void setRedirectHttpToHttps( final boolean redirectHttpToHttps )
    {
        this.redirectHttpToHttps = redirectHttpToHttps;
    }


    public boolean isHttp2()
    {
        return isHttp2;
    }


    public void setHttp2( final boolean http2 )
    {
        isHttp2 = http2;
    }


    public UUID getCorrelationId()
    {
        return correlationId;
    }


    public void setCorrelationId( final UUID correlationId )
    {
        this.correlationId = correlationId;
    }


    @Override
    public String toString()
    {
        return "PortMapDto{" + "containerSSId=" + containerSSId + ", protocol=" + protocol + ", internalPort="
                + internalPort + ", externalPort=" + externalPort + ", state=" + state + ", domain=" + domain
                + ", sslBackend=" + sslBackend + ", ipAddr=" + ipAddr + ", isProxied=" + isProxied + ", correlationId="
                + correlationId + '}';
    }
}
