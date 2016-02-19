package io.subutai.core.channel.impl.interceptor;


import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import com.google.common.base.Strings;

import io.subutai.common.settings.ChannelSettings;
import io.subutai.core.channel.impl.ChannelManagerImpl;
import io.subutai.core.channel.impl.util.InterceptorState;
import io.subutai.core.channel.impl.util.MessageContentUtil;
import io.subutai.core.identity.api.model.Session;

import org.apache.commons.lang3.exception.ExceptionUtils;


/**
 * CXF interceptor that controls channel (tunnel)
 */
public class AccessControlInterceptor extends AbstractPhaseInterceptor<Message>
{
    private static final Logger LOG = LoggerFactory.getLogger( AccessControlInterceptor.class );
    private ChannelManagerImpl channelManagerImpl = null;


    public AccessControlInterceptor( ChannelManagerImpl channelManagerImpl )
    {
        super( Phase.RECEIVE );
        this.channelManagerImpl = channelManagerImpl;
    }


    /**
     * Intercepts a message. Interceptors should NOT invoke handleMessage or handleFault on the next interceptor - the
     * interceptor chain will take care of this.
     */
    @Override
    public void handleMessage( final Message message )
    {
        try
        {
            if ( InterceptorState.SERVER_IN.isActive( message ) )
            {
                HttpServletRequest req = ( HttpServletRequest ) message.get( AbstractHTTPDestination.HTTP_REQUEST );
                Session userSession = null;

                if ( req.getLocalPort() == ChannelSettings.SECURE_PORT_X2 )
                {
                    userSession = authenticateAccess( null,null );
                }
                else
                {
                    int status = 0;
                    status = MessageContentUtil.checkUrlAccessibility( status, req );
                    //----------------------------------------------------------------------------------------------
                    if ( status == 1 ) //require tokenauth
                    {
                        userSession = authenticateAccess( message,req );
                    }
                    else if ( status == 0 ) // auth with system user
                    {
                        userSession = authenticateAccess( null,null );
                    }
                    else if ( status == 2 )
                    {
                        MessageContentUtil.abortChain( message, 403, "Permission denied" );
                    }
                }

                //******Authenticate************************************************
                if ( userSession != null )
                {
                    Subject.doAs( userSession.getSubject(), new PrivilegedAction<Void>()
                    {
                        @Override
                        public Void run()
                        {
                            try
                            {
                                message.getInterceptorChain().doIntercept( message );
                            }
                            catch ( Exception ex )
                            {
                                Throwable t = ExceptionUtils.getRootCause( ex );
                                MessageContentUtil.abortChain( message, t );
                            }
                            return null;
                        }
                    } );
                }
                else
                {
                    MessageContentUtil.abortChain( message, 401, "User is not authorized" );
                }
            }
                //-----------------------------------------------------------------------------------------------
        }
        catch ( Exception e )
        {
            throw new Fault( e );
        }
    }


    //******************************************************************
    private Session authenticateAccess( Message message, HttpServletRequest req )
    {
        if ( message == null )
        {
            //***********internal auth ********* for regisration and 8444 port
            return channelManagerImpl.getIdentityManager().login( "internal", "secretSubutai" );
        }
        else
        {
            String sptoken = req.getParameter( "sptoken" );

            if ( Strings.isNullOrEmpty( sptoken ) )
            {
                HttpHeaders headers = new HttpHeadersImpl( message.getExchange().getInMessage() );
                sptoken = headers.getHeaderString( "sptoken" );
            }

            //******************Get sptoken from cookies *****************

            if ( Strings.isNullOrEmpty( sptoken ) )
            {
                Cookie[] cookies = req.getCookies();
                for ( final Cookie cookie : cookies )
                {
                    if ( "sptoken".equals( cookie.getName() ) )
                    {
                        sptoken = cookie.getValue();
                    }
                }
            }

            if ( Strings.isNullOrEmpty( sptoken ) )
            {
                return null;
            }
            else
            {
                return channelManagerImpl.getIdentityManager().login( "token", sptoken );
            }
        }
    }
    //******************************************************************
}