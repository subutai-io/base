package io.subutai.core.hostregistry.impl;


import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.host.HostId;
import io.subutai.common.host.ResourceHostInfo;
import io.subutai.common.host.ResourceHostInfoModel;
import io.subutai.common.metric.ExceededQuota;
import io.subutai.common.metric.QuotaAlertValue;
import io.subutai.common.resource.ByteUnit;
import io.subutai.common.resource.ContainerResourceType;
import io.subutai.common.resource.ByteValueResource;
import io.subutai.common.resource.NumericValueResource;


/**
 * Heartbeat response from resource host
 */
public class HeartBeat
{
    private static final Logger LOG = LoggerFactory.getLogger( HeartBeat.class );
    ResourceHostInfoModel response;
    Set<QuotaAlertValue> alerts;


    public ResourceHostInfo getHostInfo()
    {
        return response;
    }


    public Set<QuotaAlertValue> getAlerts()
    {
        if ( alerts == null && response != null && response.getAlerts() != null )
        {
            alerts = new HashSet<>();
            for ( ResourceHostInfoModel.Alert a : response.getAlerts() )
            {
                processCpuAlert( a );
                processRamAlert( a );
                processHddAlert( a );
            }
        }

        return alerts;
    }


    private void processCpuAlert( final ResourceHostInfoModel.Alert a )
    {
        if ( a.getCpu() != null )
        {
            try
            {

                QuotaAlertValue cpuAlert = new QuotaAlertValue(
                        new ExceededQuota( new HostId( a.getId() ), ContainerResourceType.CPU,
                                new NumericValueResource( a.getCpu().getCurrent() ),
                                new NumericValueResource( a.getCpu().getQuota() ) ) );
                alerts.add( cpuAlert );
            }
            catch ( Exception e )
            {
                LOG.warn( "CPU alert parse error: " + e.getMessage() );
            }
        }
    }


    private void processRamAlert( final ResourceHostInfoModel.Alert a )
    {
        if ( a.getRam() != null )
        {
            try
            {
                QuotaAlertValue ramAlert = new QuotaAlertValue(
                        new ExceededQuota( new HostId( a.getId() ), ContainerResourceType.RAM,
                                new ByteValueResource( a.getRam().getCurrent(), ByteUnit.MB ),
                                new ByteValueResource( a.getRam().getQuota(), ByteUnit.MB ) ) );

                alerts.add( ramAlert );
            }
            catch ( Exception e )
            {
                LOG.warn( "RAM alert parse error: " + e.getMessage() );
            }
        }
    }


    private void processHddAlert( final ResourceHostInfoModel.Alert a )
    {
        if ( a.getRam() != null )
        {
            try
            {
                QuotaAlertValue cpuAlert = new QuotaAlertValue(
                        new ExceededQuota( new HostId( a.getId() ), ContainerResourceType.RAM,
                                new ByteValueResource( a.getRam().getCurrent(), ByteUnit.MB ),
                                new ByteValueResource( a.getRam().getQuota(), ByteUnit.MB ) ) );
                alerts.add( cpuAlert );
            }
            catch ( Exception e )
            {
                LOG.warn( "HDD alert parse error: " + e.getMessage() );
            }
        }
    }
}
