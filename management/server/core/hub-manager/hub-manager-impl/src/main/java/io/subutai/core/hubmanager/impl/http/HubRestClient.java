package io.subutai.core.hubmanager.impl.http;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.Response;

import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.subutai.core.hubmanager.api.RestClient;
import io.subutai.core.hubmanager.api.RestResult;
import io.subutai.core.hubmanager.api.exception.HubManagerException;
import io.subutai.core.hubmanager.impl.ConfigManager;
import io.subutai.hub.share.json.JsonUtil;


public class HubRestClient implements RestClient
{
    public static final String CONNECTION_EXCEPTION_MARKER = "ConnectException";
    private static final String ERROR = "Error executing request to Hub: ";

    private final Logger log = LoggerFactory.getLogger( getClass() );

    private final ConfigManager configManager;


    public HubRestClient( ConfigManager configManager )
    {
        this.configManager = configManager;
    }


    @Override
    public <T> RestResult<T> get( String url, Class<T> clazz )
    {
        return execute( "GET", url, null, clazz, true );
    }


    /**
     * Throws exception if result is not successful
     */
    @Override
    public <T> T getStrict( String url, Class<T> clazz ) throws HubManagerException
    {
        RestResult<T> restResult = get( url, clazz );

        if ( !restResult.isSuccess() )
        {
            throw new HubManagerException( restResult.getError() );
        }

        return restResult.getEntity();
    }


    @Override
    public <T> RestResult<T> post( String url, Object body, Class<T> clazz )
    {
        return execute( "POST", url, body, clazz, true );
    }


    @Override
    public RestResult<Object> post( String url, Object body )
    {
        return post( url, body, Object.class );
    }


    /**
     * Executes POST request without encrypting body and response
     */
    @Override
    public RestResult<Object> postPlain( String url, Object body )
    {
        return execute( "POST", url, body, Object.class, false );
    }


    @Override
    public <T> RestResult<T> put( String url, Object body, Class<T> clazz )
    {
        return execute( "PUT", url, body, clazz, true );
    }


    @Override
    public RestResult<Object> delete( String url )
    {
        return execute( "DELETE", url, null, Object.class, false );
    }


    private <T> RestResult<T> execute( String httpMethod, String url, Object body, Class<T> clazz, boolean encrypt )
    {
        log.info( "{} {}", httpMethod, url );

        WebClient webClient = null;
        Response response = null;
        RestResult<T> restResult = new RestResult<>( HttpStatus.SC_INTERNAL_SERVER_ERROR );

        try
        {
            webClient = configManager.getTrustedWebClientWithAuth( url, configManager.getHubIp() );

            Object requestBody = encrypt ? encryptBody( body ) : body;

            response = webClient.invoke( httpMethod, requestBody );

            log.info( "response.status: {} - {}", response.getStatus(), response.getStatusInfo().getReasonPhrase() );

            restResult = handleResponse( response, clazz, encrypt );
        }
        catch ( Exception e )
        {
            if ( ExceptionUtils.getStackTrace( e ).contains( CONNECTION_EXCEPTION_MARKER ) )
            {
                restResult.setError( CONNECTION_EXCEPTION_MARKER );
            }
            else
            {
                restResult.setError( ERROR + e.getMessage() );
            }

            log.error( ERROR + e.getMessage() );
        }
        finally
        {
            close( webClient, response );
        }

        return restResult;
    }


    private void close( WebClient webClient, Response response )
    {
        if ( response != null )
        {
            response.close();
        }

        if ( webClient != null )
        {
            webClient.close();
        }
    }


    private byte[] encryptBody( Object body ) throws PGPException, JsonProcessingException
    {
        byte[] cborData = JsonUtil.toCbor( body );

        return configManager.getMessenger().produce( cborData );
    }


    private <T> RestResult<T> handleResponse( Response response, Class<T> clazz, boolean encrypt )
            throws IOException, PGPException
    {
        RestResult<T> restResult = new RestResult<>( response.getStatus() );

        if ( !restResult.isSuccess() )
        {
            String error = response.readEntity( String.class );

            restResult.setError( error );

            log.error( "error: {}", StringUtils.abbreviate( error, 250 ) );

            return restResult;
        }

        if ( encrypt )
        {
            byte[] encryptedContent = readContent( response );

            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );

            restResult.setEntity( JsonUtil.fromCbor( plainContent, clazz ) );
        }

        return restResult;
    }


    private byte[] readContent( Response response ) throws IOException
    {
        if ( response.getEntity() == null )
        {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        InputStream is = ( InputStream ) response.getEntity();

        IOUtils.copy( is, bos );

        return bos.toByteArray();
    }
}
