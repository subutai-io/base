package io.subutai.core.template.impl;


import java.util.Set;

import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.cache.LoadingCache;

import io.subutai.common.protocol.Template;
import io.subutai.core.identity.api.IdentityManager;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


@RunWith( MockitoJUnitRunner.class )
public class TemplateManagerImplTest
{

    private final static String TEMPLATE_ID = "public.c2deca182fe8cb8e747065b6eda5920b";
    private final static String TEMPLATE_NAME = "httpd";
    private final static String GORJUN_LIST_OUTPUT = String.format( "[{\"id\":\"%s\","
                    + "\"name\":\"%s\"},{\"id\":\"public.6cc434a73bf7df6e9d2b8f0cf3feacec\",\"name\":\"rabbitmq\"}]",
            TEMPLATE_ID, TEMPLATE_NAME );
    private final static String GORJUN_TEMPLATE_OUTPUT =
            String.format( "{\"id\":\"%s\"," + "\"name\":\"%s\"}", TEMPLATE_ID, TEMPLATE_NAME );
    private TemplateManagerImpl templateManager;

    @Mock
    WebClient webClient;
    @Mock
    Response response;
    @Mock
    LoadingCache<String, Template> cache;
    @Mock
    Template template;
    @Mock
    IdentityManager identityManager;


    @Before
    public void setUp() throws Exception
    {

        templateManager = spy( new TemplateManagerImpl( identityManager ) );

        doReturn( webClient ).when( templateManager ).getWebClient( anyString() );
        doReturn( response ).when( webClient ).get();
        doReturn( GORJUN_LIST_OUTPUT ).when( response ).readEntity( String.class );
        doReturn( cache ).when( templateManager ).getVerifiedTemplatesCache();
        doReturn( null ).when( cache ).get( TEMPLATE_NAME );
    }


    @Test
    public void testGetTemplates() throws Exception
    {
        Set<Template> templates = templateManager.getTemplates();

        assertFalse( templates.isEmpty() );
    }


    @Test
    public void testGetTemplate() throws Exception
    {
        Template template = templateManager.getTemplate( TEMPLATE_ID );

        assertNotNull( template );
    }


    @Test
    public void testGetTemplateByName() throws Exception
    {
        Template template = templateManager.getTemplateByName( TEMPLATE_NAME );

        assertNotNull( template );
    }


    @Test
    public void testGetVerifiedTemplateByName() throws Exception
    {
        doReturn( template ).when( cache ).get( TEMPLATE_NAME );
        doReturn( GORJUN_TEMPLATE_OUTPUT ).when( response ).readEntity( String.class );

        Template template = templateManager.getVerifiedTemplateByName( TEMPLATE_NAME );

        assertNotNull( template );
    }
}
