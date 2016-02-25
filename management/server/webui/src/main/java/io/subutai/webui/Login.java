package io.subutai.webui;


import java.io.IOException;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.subutai.common.util.ServiceLocator;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.identity.api.model.User;


public class Login extends HttpServlet
{
    private static final Logger logger = LoggerFactory.getLogger(Login.class );
    protected void doPost( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
    {
        String username = request.getParameter( "username" );
        String password = request.getParameter( "password" );

        if ( !Strings.isNullOrEmpty( username ) )
        {
            try
            {
                IdentityManager identityManager = ServiceLocator.getServiceNoCache( IdentityManager.class );

                String token = null;
                if ( identityManager != null )
                {
                    token = identityManager.getUserToken( username, password );
                }

                if ( !Strings.isNullOrEmpty( token ) )
                {
                    User user = identityManager.authenticateByToken( token );
                    request.getSession().setAttribute( "userSessionData", token );
                    Cookie sptoken = new Cookie( "sptoken", token );
//                    sptoken.setMaxAge( 3600 * 24 * 7 * 365 * 10 );

                    logger.info(user.getFingerprint());
                    logger.info(user.getEmail());
                    logger.info(user.getFullName());
                    logger.info(user.getSecurityKeyId());
                    logger.info(user.getUserName());
                    Cookie fingerprint = new Cookie( "su_fingerprint", user.getFingerprint() );
//                    fingerprint.setMaxAge( 3600 * 24 * 7 * 365 * 10 );

                    response.addCookie( sptoken );
                    response.addCookie( fingerprint );
                }
                else
                {
                    request.setAttribute( "error", "Wrong Username or Password !!!" );
                    response.getWriter().write( "Error, Wrong Username or Password" );
                    response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
                }
            }
            catch ( NamingException e )
            {
                request.setAttribute( "error", "karaf exceptions !!!" );
                response.getWriter().write( "Error, karaf exceptions !!!" );
                response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            }
        }
        else
        {
            request.setAttribute( "error", "Please enter username or password" );
            response.getWriter().write( "Error, Please enter username or password" );
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
        }
    }
}