package io.subutai.core.network.impl;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandUtil;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.host.HostInterface;
import io.subutai.common.peer.ContainerHost;
import io.subutai.core.network.api.NetworkManagerException;

import com.google.common.collect.Sets;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class HostManagerTest
{

    private static final String DOMAIN = "domain";
    @Mock
    ContainerHost containerHost;
    @Mock
    CommandUtil commandUtil;

    private HostManager hostManager;
    @Mock
    private HostInterface hostInterface;


    @Before
    public void setUp() throws Exception
    {
        when( containerHost.getInterfaceByName( anyString() ) ).thenReturn( hostInterface );
        hostManager = new HostManager( Sets.newHashSet( containerHost ), DOMAIN );
        hostManager.commandUtil = commandUtil;
    }


    @Test( expected = NetworkManagerException.class )
    public void testExecute() throws Exception
    {

        hostManager.execute();

        verify( commandUtil ).execute( any( RequestBuilder.class ), eq( containerHost ) );

        doThrow( new CommandException( "" ) ).when( commandUtil )
                                             .execute( any( RequestBuilder.class ), eq( containerHost ) );

        hostManager.execute();
    }
}
