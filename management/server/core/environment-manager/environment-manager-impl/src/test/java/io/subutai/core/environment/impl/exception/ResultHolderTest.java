package io.subutai.core.environment.impl.exception;


import org.junit.Test;

import io.subutai.core.environment.impl.exception.ResultHolder;

import static junit.framework.TestCase.assertEquals;


public class ResultHolderTest
{
    @Test
    public void testSetterNGetter() throws Exception
    {

        Object result = new Object();
        ResultHolder<Object> resultHolder = new ResultHolder<>();

        resultHolder.setResult( result );

        assertEquals( result, resultHolder.getResult() );
    }
}
