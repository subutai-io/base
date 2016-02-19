package io.subutai.core.channel.impl.util;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.AccessControlException;

import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import io.subutai.common.settings.ChannelSettings;
import io.subutai.core.security.api.SecurityManager;
import io.subutai.core.security.api.crypto.EncryptionTool;
import io.subutai.core.security.api.crypto.KeyManager;


/**
 *
 */
public class MessageContentUtil
{
    private static final Logger LOG = LoggerFactory.getLogger( MessageContentUtil.class );


    //***************************************************************************
    public static void abortChain( Message message, Throwable ex )
    {
        if ( ex.getClass() == AccessControlException.class )
        {
            abortChain( message, 403, "Access Denied to the resource" );
        }
        else if ( ex.getClass() == LoginException.class )
        {
            abortChain( message, 401, "User is not authorized" );
        }
        else
        {
            abortChain( message, 500, "Internal system Error 500" );
        }

        LOG.error( "****** Error !! Error in doIntercept:" + ex.toString(), ex );
    }


    //***************************************************************************
    public static void abortChain( Message message, int errorStatus, String errorMessage )
    {
        HttpServletResponse response = ( HttpServletResponse ) message.getExchange().getInMessage()
                                                                      .get( AbstractHTTPDestination.HTTP_RESPONSE );
        try
        {
            response.setStatus( errorStatus );
            response.getOutputStream().write( errorMessage.getBytes( Charset.forName( "UTF-8" ) ) );
            response.getOutputStream().flush();
            LOG.error( "****** Error !! Error in doIntercept:" + errorMessage );
        }
        catch ( Exception e )
        {
            //ignore
            LOG.error( "Error writing to response: " + e.toString(), e );
        }

        message.getInterceptorChain().abort();
    }


    //***************************************************************************
    public static int checkUrlAccessibility( final int currentStatus, HttpServletRequest req )
    {
        int status = currentStatus;
        int inPort = req.getLocalPort();
        String basePath = req.getRequestURI();


        if ( inPort == ChannelSettings.SECURE_PORT_X1 )
        {
            if ( ChannelSettings.checkURLAccess( basePath, ChannelSettings.URL_ACCESS_PX1 ) == 0 )
            {
                status = 1;
            }
        }
        else if ( inPort == ChannelSettings.OPEN_PORT )
        {
            if ( ChannelSettings.checkURLAccess( basePath, ChannelSettings.URL_ACCESS_PX1 ) == 0 )
            {
                status = 1;
            }
        }
        else if ( inPort ==  ChannelSettings.SPECIAL_PORT_X1 ) //file server
        {
            if ( basePath.startsWith( "/rest/kurjun" ) )
            {
                status = 1;
            }
            else
            {
                status = 2;
            }
        }
        else
        {
            status = 0;
        }

        return status;
    }


    /* ******************************************************
     *
     */
    public static void decryptContent( SecurityManager securityManager, Message message, String hostIdSource,
                                       String hostIdTarget )
    {

        InputStream is = message.getContent( InputStream.class );

        CachedOutputStream os = new CachedOutputStream();

        LOG.debug( String.format( "Decrypting IDs: %s -> %s", hostIdSource, hostIdTarget ) );
        try
        {
            int copied = IOUtils.copyAndCloseInput( is, os );
            os.flush();

            byte[] data = copied > 0 ? decryptData( securityManager, hostIdSource, hostIdTarget, os.getBytes() ) : null;
            org.apache.commons.io.IOUtils.closeQuietly( os );

            if ( data != null )
            {
                LOG.debug( String.format( "Decrypted payload: \"%s\"", new String( data ) ) );
                message.setContent( InputStream.class, new ByteArrayInputStream( data ) );
            }
            else
            {
                LOG.debug( "Decrypted data is NULL!!!" );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Error decrypting content", e );
        }
    }


    /* ******************************************************
     *
     */
    private static byte[] decryptData( SecurityManager securityManager, String hostIdSource, String hostIdTarget,
                                       byte[] data ) throws PGPException
    {

        try
        {
            if ( data == null || data.length == 0 )
            {
                return null;
            }
            else
            {
                EncryptionTool encTool = securityManager.getEncryptionTool();

                //encTool.

                KeyManager keyMan = securityManager.getKeyManager();
                PGPSecretKeyRing secKey = keyMan.getSecretKeyRing( hostIdSource );

                if ( secKey != null )
                {
                    LOG.debug( " ****** Decrypting with: " + hostIdSource + " ****** " );
                    byte[] outData = encTool.decrypt( data, secKey, "" );
                    //byte[] outData = encTool.decryptAndVerify();
                    return outData;
                }
                else
                {
                    LOG.debug( String.format( " ****** Decryption error. Could not find Secret key : %s ****** ",
                            hostIdSource ) );
                    throw new PGPException( "Cannot find Secret Key" );
                }
            }
        }
        catch ( Exception ex )
        {
            throw new PGPException( ex.toString() );
        }
    }


    private static URL getURL( final Message message ) throws MalformedURLException
    {
        return new URL( ( String ) message.getExchange().getOutMessage().get( Message.ENDPOINT_ADDRESS ) );
    }


    /* ******************************************************
    *
    */
    public static void encryptContent( SecurityManager securityManager, String hostIdSource, String hostIdTarget,
                                       Message message )
    {
        OutputStream os = message.getContent( OutputStream.class );

        CachedStream cs = new CachedStream();
        message.setContent( OutputStream.class, cs );

        message.getInterceptorChain().doIntercept( message );
        LOG.debug( String.format( "Encrypting IDs: %s -> %s", hostIdSource, hostIdTarget ) );

        try
        {
            cs.flush();
            CachedOutputStream csnew = ( CachedOutputStream ) message.getContent( OutputStream.class );

            byte[] originalMessage = org.apache.commons.io.IOUtils.toByteArray( csnew.getInputStream() );
            LOG.debug( String.format( "Original payload: \"%s\"", new String( originalMessage ) ) );

            csnew.flush();
            org.apache.commons.io.IOUtils.closeQuietly( cs );
            org.apache.commons.io.IOUtils.closeQuietly( csnew );

            //do something with original message to produce finalMessage
            byte[] finalMessage = originalMessage.length > 0 ?
                                  encryptData( securityManager, hostIdSource, hostIdTarget, originalMessage ) : null;

            if ( finalMessage != null )
            {

                InputStream replaceInStream = new ByteArrayInputStream( finalMessage );

                org.apache.commons.io.IOUtils.copy( replaceInStream, os );
                replaceInStream.close();
                org.apache.commons.io.IOUtils.closeQuietly( replaceInStream );

                os.flush();
                message.setContent( OutputStream.class, os );
            }


            org.apache.commons.io.IOUtils.closeQuietly( os );
        }
        catch ( Exception ioe )
        {
            throw new RuntimeException( ioe );
        }
    }


    /* ******************************************************
     *
     */


    private static byte[] encryptData( SecurityManager securityManager, String hostIdSource, String hostIdTarget,
                                       byte[] data ) throws PGPException
    {
        try
        {
            if ( data == null || data.length == 0 )
            {
                return null;
            }
            else
            {
                EncryptionTool encTool = securityManager.getEncryptionTool();
                KeyManager keyMan = securityManager.getKeyManager();
                PGPPublicKey pubKey = keyMan.getRemoteHostPublicKey( hostIdTarget, "UNKNOWN" );

                if ( pubKey != null )
                {
                    LOG.debug( String.format( " ****** Encrypting with %s ****** ", hostIdTarget ) );
                    byte[] outData = encTool.encrypt( data, pubKey, true );
                    //byte[] outData = encTool.signAndEncrypt(  data, pubKey, false );
                    return outData;
                }
                else
                {
                    LOG.debug( String.format( " ****** Encryption error. Could not find Public key : %s ****** ",
                            hostIdTarget ) );
                    throw new PGPException( "Cannot find Public Key" );
                }
            }
        }
        catch ( Exception ex )
        {
            throw new PGPException( ex.toString() );
        }
    }
}