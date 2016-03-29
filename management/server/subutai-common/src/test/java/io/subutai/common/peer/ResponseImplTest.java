package io.subutai.common.peer;


import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Sets;

import io.subutai.common.command.Response;
import io.subutai.common.command.ResponseImpl;
import io.subutai.common.command.ResponseType;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class ResponseImplTest
{
    private static final ResponseType RESPONSE_TYPE = ResponseType.EXECUTE_RESPONSE;
    private static final String ID = UUID.randomUUID().toString();
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final Integer PID = 123;
    private static final Integer RES_NO = 1;
    private static final String STD_OUT = "out";
    private static final String STD_ERR = "err";
    private static final Integer EXIT_CODE = 0;

    @Mock
    Response response;

    ResponseImpl responseImpl;


    @Before
    public void setUp() throws Exception
    {
        when( response.getType() ).thenReturn( RESPONSE_TYPE );
        when( response.getId() ).thenReturn( ID );
        when( response.getCommandId() ).thenReturn( COMMAND_ID );
        when( response.getPid() ).thenReturn( PID );
        when( response.getResponseNumber() ).thenReturn( RES_NO );
        when( response.getStdOut() ).thenReturn( STD_OUT );
        when( response.getStdErr() ).thenReturn( STD_ERR );
        when( response.getExitCode() ).thenReturn( EXIT_CODE );

        responseImpl = new ResponseImpl( response );
    }


    @Test
    public void testProperties() throws Exception
    {
        assertEquals( RESPONSE_TYPE, responseImpl.getType() );

        assertEquals( ID, responseImpl.getId() );

        assertEquals( COMMAND_ID, responseImpl.getCommandId() );

        assertEquals( PID, responseImpl.getPid() );

        assertEquals( RES_NO, responseImpl.getResponseNumber() );

        assertEquals( STD_OUT, responseImpl.getStdOut() );

        assertEquals( STD_ERR, responseImpl.getStdErr() );

        assertEquals( EXIT_CODE, responseImpl.getExitCode() );

    }
}
