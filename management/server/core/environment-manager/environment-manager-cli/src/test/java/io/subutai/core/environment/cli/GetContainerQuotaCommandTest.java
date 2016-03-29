package io.subutai.core.environment.cli;


import java.util.UUID;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.subutai.common.environment.Environment;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.Peer;
import io.subutai.common.test.SystemOutRedirectTest;
import io.subutai.core.environment.api.EnvironmentManager;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class GetContainerQuotaCommandTest extends SystemOutRedirectTest
{
    private static final String CONTAINER_NAME = "container";

    @Mock
    EnvironmentManager environmentManager;
    @Mock
    Peer peer;
    @Mock
    Environment environment;
    @Mock
    EnvironmentContainerHost containerHost;

    GetContainerQuotaCommand command;


    @Before
    public void setUp() throws Exception
    {
        command = new GetContainerQuotaCommand( environmentManager );
        when( environmentManager.loadEnvironment( any( String.class ) ) ).thenReturn( environment );
        when( environment.getContainerHostByHostname( anyString() ) ).thenReturn( containerHost );
        command.environmentId = UUID.randomUUID().toString();
        command.containerId = CONTAINER_NAME;
    }


    @Test
    @Ignore
    public void testDoExecute() throws Exception
    {
        command.doExecute();

        //        verify( peer ).getQuotaInfo( containerHost, QuotaType.QUOTA_TYPE_CPU );

        when( environment.getContainerHostByHostname( anyString() ) ).thenReturn( null );

        command.doExecute();

        assertTrue( getSysOut().contains( CONTAINER_NAME ) );
    }
}
