package io.subutai.core.hostregistry.impl;


import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.HeartBeat;
import io.subutai.common.host.HostArchitecture;
import io.subutai.common.metric.QuotaAlert;
import io.subutai.common.metric.QuotaAlertValue;
import io.subutai.common.resource.ContainerResourceType;
import io.subutai.common.settings.Common;
import io.subutai.common.util.JsonUtil;
import io.subutai.common.host.ResourceHostInfo;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertNotNull;


public class HeartBeatTest
{
    private static final String HOST_HOSTNAME = "host";
    private static final String HOST_ID = UUID.randomUUID().toString();
    private static final String HOST_IP = "127.0.0.2";
    private static final String HOST_MAC_ADDRESS = "0c:8b:fd:c0:ea:fe";
    private static final String CONTAINER_HOSTNAME = "container";
    private static final String CONTAINER_ID = UUID.randomUUID().toString();
    private static final String CONTAINER_IP = "127.0.0.1";
    private static final String CONTAINER_INTERFACE = "eth0";
    private static final String CONTAINER_MAC_ADDRESS = "0c:8b:fd:c0:ea:fe";
    private static final ContainerHostState CONTAINER_STATUS = ContainerHostState.FROZEN;
    private static final HostArchitecture ARCH = HostArchitecture.AMD64;
    private static final String ALERT = "                \"alert\": [\n" + "                    {\n"
            + "                        \"id\": \"CE399A38D78A8815C95A87A5B54FA60751092C73\",\n"
            + "                        \"cpu\": {\n" + "                            \"current\": 87,\n"
            + "                            \"quota\": 15\n" + "                        },\n"
            + "                        \"ram\": {\n" + "                            \"current\": 90,\n"
            + "                            \"quota\": 1024\n" + "                        },\n"
            + "                        \"hdd\": [\n" + "                            {\n"
            + "                                \"partition\": \"Var\",\n"
            + "                                \"current\": 86,\n" + "                                \"quota\": 10\n"
            + "                            },\n" + "                            {\n"
            + "                                \"partition\": \"Opt\",\n"
            + "                                \"current\": 86,\n" + "                                \"quota\": 10\n"
            + "                            }\n" + "                        ]\n" + "                    }\n"
            + "                ]\n";
    private static final String INFO_JSON = String.format(
            "{ \"response\" : {\"type\":\"HEARTBEAT\", \"hostname\":\"%s\", \"id\":\"%s\", \"arch\":\"%s\", " +
                    "\"interfaces\" : [{ \"interfaceName\":\"%s\", \"ip\":\"%s\",\"mac\":\"%s\"}], "
                    + "\"containers\": [{ \"hostname\":\"%s\", \"id\":\"%s\", " +
                    "\"interfaces\" : [{ \"interfaceName\":\"%s\", \"ip\":\"%s\",\"mac\":\"%s\"}], " +
                    "\"status\":\"%s\" , \"arch\":\"%s\"}]},%s}", HOST_HOSTNAME, HOST_ID, ARCH,
            Common.DEFAULT_CONTAINER_INTERFACE, HOST_IP, HOST_MAC_ADDRESS, CONTAINER_HOSTNAME, CONTAINER_ID,
            CONTAINER_INTERFACE, CONTAINER_IP, CONTAINER_MAC_ADDRESS, CONTAINER_STATUS, ARCH, ALERT );

    HeartBeat heartBeat;


    @Before
    public void setUp() throws Exception
    {
//        System.out.println(INFO_JSON);
        heartBeat = JsonUtil.fromJson( INFO_JSON, HeartBeat.class );
    }


    @Test
    public void testGetHostInfo() throws Exception
    {
        ResourceHostInfo resourceHostInfo = heartBeat.getHostInfo();

        assertNotNull( resourceHostInfo );
        assertFalse( resourceHostInfo.getContainers().isEmpty() );
    }

//    @Test
//    public void testAlert() throws Exception
//    {
//        final Set<QuotaAlertValue> alerts = heartBeat.getAlerts();
//
//        assertNotNull( alerts );
//        QuotaAlertValue q = alerts.iterator().next();
//        assertEquals( ContainerResourceType.CPU, q.getValue().getContainerResourceType() );
//    }
}
