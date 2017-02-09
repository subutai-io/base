package io.subutai.common.task;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.subutai.common.host.HostArchitecture;
import io.subutai.common.settings.Common;
import io.subutai.hub.share.quota.ContainerQuota;


public class CloneRequest
{
    @JsonProperty( value = "resourceHostId" )
    private final String resourceHostId;

    @JsonProperty( value = "hostname" )
    private String hostname;

    @JsonProperty( value = "containerName" )
    private String containerName;

    @JsonProperty( value = "ip" )
    private final String ip;

    @JsonProperty( value = "templateId" )
    private final String templateId;

    @JsonProperty( value = "templateArch" )
    private final HostArchitecture templateArch;

    @JsonProperty( value = "containerQuota" )
    private final ContainerQuota containerQuota;

    @JsonProperty( value = "kurjunToken" )
    private final String kurjunToken;


    public CloneRequest( @JsonProperty( value = "resourceHostId" ) final String resourceHostId,
                         @JsonProperty( value = "hostname" ) final String hostname,
                         @JsonProperty( value = "containerName" ) final String containerName,
                         @JsonProperty( value = "ip" ) final String ip,
                         @JsonProperty( value = "templateId" ) final String templateId,
                         @JsonProperty( value = "templateArch" ) HostArchitecture templateArch,
                         @JsonProperty( value = "containerQuota" ) final ContainerQuota containerQuota,
                         @JsonProperty( value = "kurjunToken" ) final String kurjunToken )
    {
        Preconditions.checkNotNull( resourceHostId );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ) );
        Preconditions.checkNotNull( templateId );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( ip ) && ip.matches( Common.CIDR_REGEX ) );

        this.resourceHostId = resourceHostId;
        this.hostname = hostname;
        this.containerName = containerName;
        this.ip = ip;
        this.templateId = templateId;
        this.templateArch = templateArch;
        this.containerQuota = containerQuota;
        this.kurjunToken = kurjunToken;
    }


    public String getResourceHostId()
    {
        return resourceHostId;
    }


    public String getHostname()
    {
        return hostname;
    }


    public void setHostname( final String hostname )
    {
        this.hostname = hostname;
    }


    public String getContainerName()
    {
        return containerName;
    }


    public void setContainerName( final String containerName )
    {
        this.containerName = containerName;
    }


    public String getIp()
    {
        return ip;
    }


    public String getTemplateId()
    {
        return templateId;
    }


    public HostArchitecture getTemplateArch()
    {
        return templateArch;
    }


    public ContainerQuota getContainerQuota()
    {
        return containerQuota;
    }


    public String getKurjunToken()
    {
        return kurjunToken;
    }


    @Override
    public String toString()
    {
        return "CloneRequest{" + "resourceHostId='" + resourceHostId + '\'' + ", hostname='" + hostname + '\''
                + ", containerName='" + containerName + '\'' + ", ip='" + ip + '\'' + ", templateId='" + templateId
                + '\'' + ", templateArch=" + templateArch + ", containerQuota=" + containerQuota + ", kurjunToken="
                + kurjunToken + '}';
    }
}
