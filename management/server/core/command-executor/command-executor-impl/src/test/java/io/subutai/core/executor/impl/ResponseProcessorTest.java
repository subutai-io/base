package io.subutai.core.executor.impl;


import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.Request;
import io.subutai.common.command.Response;

import static junit.framework.TestCase.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class ResponseProcessorTest
{
    @Mock
    CommandProcess commandProcess;
    @Mock
    CommandProcessor commandProcessor;
    @Mock
    Response response;
    @Mock
    CommandCallback callback;
    @Mock
    Request request;

    ResponseProcessor responseProcessor;


    @Before
    public void setUp() throws Exception
    {
        responseProcessor = new ResponseProcessor( response, commandProcess, commandProcessor, request );
        when( commandProcess.getCallback() ).thenReturn( callback );
        when( commandProcess.isDone() ).thenReturn( true );
    }


    @Test
    public void testConstructor() throws Exception
    {
        try
        {
            new ResponseProcessor( null, commandProcess, commandProcessor, request );
            fail( "Expected NullPointerException" );
        }
        catch ( NullPointerException e )
        {
        }
        try
        {
            new ResponseProcessor( response, null, commandProcessor, request );
            fail( "Expected NullPointerException" );
        }
        catch ( NullPointerException e )
        {
        }
        try
        {
            new ResponseProcessor( response, commandProcess, null, request );
            fail( "Expected NullPointerException" );
        }
        catch ( NullPointerException e )
        {
        }
    }


    @Test
    public void testRun() throws Exception
    {

        responseProcessor.run();

        verify( commandProcess ).appendResponse( response );
        verify( commandProcess ).getCallback();
        verify( callback ).onResponse( eq( response ), any( CommandResult.class ) );
        verify( commandProcess ).isDone();
        verify( commandProcessor ).remove( any( Request.class ) );
        verify( commandProcess ).stop();


        RuntimeException exception = mock( RuntimeException.class );
        doThrow( exception ).when( commandProcess ).isDone();

        responseProcessor.run();

        verify( exception ).printStackTrace( any( PrintStream.class ) );
    }
}
