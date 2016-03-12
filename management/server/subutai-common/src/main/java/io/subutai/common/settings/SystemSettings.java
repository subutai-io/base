package io.subutai.common.settings;


import java.net.MalformedURLException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;


public class SystemSettings
{
    private static final Logger LOG = LoggerFactory.getLogger( SystemSettings.class );

    public static final String DEFAULT_EXTERNAL_INTERFACE = "wan";
    public static final String DEFAULT_MGMT_INTERFACE = "mng-net";
    public static final String DEFAULT_PUBLIC_URL = "https://127.0.0.1:8443";
    public static final String DEFAULT_KURJUN_REPO = "http://repo.critical-factor.com:8080/rest/kurjun";
    public static final String DEFAULT_PEER_PWD = "12345678";

    private static PropertiesConfiguration PROPERTIES = null;
    private static String[] GLOBAL_KURJUN_URLS = null;


    static
    {
        loadProperties();
    }


    public static void loadProperties()
    {
        try
        {
            PROPERTIES = new PropertiesConfiguration( String.format( "%s/subutaisystem.cfg", Common.KARAF_ETC ) );
            loadGlobalKurjunUrls();
        }
        catch ( ConfigurationException e )
        {
            throw new RuntimeException( "Failed to load subutaisettings.cfg file.", e );
        }
    }

    // Kurjun Settings


    public static String[] getGlobalKurjunUrls()
    {
        return GLOBAL_KURJUN_URLS;
    }


    public static void setGlobalKurjunUrls( String[] urls ) throws ConfigurationException
    {
        String[] validated = validateGlobalKurjunUrls( urls );
        saveProperty( "globalKurjunUrls", validated );
        loadGlobalKurjunUrls();
    }


    protected static String[] validateGlobalKurjunUrls( final String[] urls ) throws ConfigurationException
    {
        String[] arr = new String[urls.length];

        for ( int i = 0; i < urls.length; i++ )
        {
            String url = urls[i];
            try
            {
                new URL( url );
                String u = url.endsWith( "/" ) ? url.replaceAll( "/+$", "" ) : url;
                arr[i] = u;
            }
            catch ( MalformedURLException e )
            {
                throw new ConfigurationException( "Invalid URL: " + url );
            }
        }
        return arr;
    }


    protected static void validatePublicUrl( String publicUrl ) throws ConfigurationException
    {
        try
        {
            new URL( publicUrl );
        }
        catch ( MalformedURLException e )
        {
            throw new ConfigurationException( "Invalid URL: " + publicUrl );
        }
    }


    private static void loadGlobalKurjunUrls() throws ConfigurationException
    {
        String[] globalKurjunUrls = PROPERTIES.getStringArray( "globalKurjunUrls" );
        if ( globalKurjunUrls.length < 1 )
        {
            globalKurjunUrls = new String[] {
                    DEFAULT_KURJUN_REPO
            };
        }

        GLOBAL_KURJUN_URLS = validateGlobalKurjunUrls( globalKurjunUrls );
    }


    // Network Settings


    public static String getExternalIpInterface()
    {
        return PROPERTIES.getString( "externalInterfaceName", DEFAULT_EXTERNAL_INTERFACE );
    }


    public static void setExternalIpInterface( String externalInterfaceName )
    {
        saveProperty( "externalInterfaceName", externalInterfaceName );
    }


    public static String getMgmtInterface()
    {
        return PROPERTIES.getString( "mgmtInterfaceName", DEFAULT_MGMT_INTERFACE );
    }


    public static void setMgmtInterface( String mgmtInterfaceName )
    {
        saveProperty( "mgmtInterfaceName", mgmtInterfaceName );
    }


    public static int getOpenPort()
    {
        return PROPERTIES.getInt( "openPort", ChannelSettings.OPEN_PORT );
    }


    public static int getSecurePortX1()
    {
        return PROPERTIES.getInt( "securePortX1", ChannelSettings.SECURE_PORT_X1 );
    }


    public static int getSecurePortX2()
    {
        return PROPERTIES.getInt( "securePortX2", ChannelSettings.SECURE_PORT_X2 );
    }


    public static int getSecurePortX3()
    {
        return PROPERTIES.getInt( "securePortX3", ChannelSettings.SECURE_PORT_X3 );
    }


    public static int getSpecialPortX1()
    {
        return PROPERTIES.getInt( "specialPortX1", ChannelSettings.SPECIAL_PORT_X1 );
    }


    public static int getAgentPort()
    {
        return PROPERTIES.getInt( "agentPort", ChannelSettings.AGENT_PORT );
    }


    public static void setOpenPort( int openPort )
    {
        saveProperty( "openPort", openPort );
    }


    public static void setSecurePortX1( int securePortX1 )
    {
        saveProperty( "securePortX1", securePortX1 );
    }


    public static void setSecurePortX2( int securePortX2 )
    {
        saveProperty( "securePortX2", securePortX2 );
    }


    public static void setSecurePortX3( int securePortX3 )
    {
        saveProperty( "securePortX3", securePortX3 );
    }


    public static void setSpecialPortX1( int specialPortX1 )
    {
        saveProperty( "specialPortX1", specialPortX1 );
    }


    public static void setAgentPort( int agentPort )
    {
        saveProperty( "agentPort", agentPort );
    }


    // Security Settings


    //todo remove this since communication is always encrypted
    @Deprecated
    public static boolean getEncryptionState()
    {
        return PROPERTIES.getBoolean( "encryptionEnabled", false );
    }


    //todo remove
    public static boolean getRestEncryptionState()
    {
        return PROPERTIES.getBoolean( "restEncryptionEnabled", false );
    }


    public static boolean getIntegrationState()
    {
        return PROPERTIES.getBoolean( "integrationEnabled", false );
    }


    public static boolean getKeyTrustCheckState()
    {
        return PROPERTIES.getBoolean( "keyTrustCheckEnabled", false );
    }


    public static void setEncryptionState( boolean encryptionEnabled )
    {
        saveProperty( "encryptionEnabled", encryptionEnabled );
    }


    public static void setRestEncryptionState( boolean restEncryptionEnabled )
    {
        saveProperty( "restEncryptionEnabled", restEncryptionEnabled );
    }


    public static void setIntegrationState( boolean integrationEnabled )
    {
        saveProperty( "integrationEnabled", integrationEnabled );
    }


    public static void setKeyTrustCheckState( boolean keyTrustCheckEnabled )
    {
        saveProperty( "keyTrustCheckEnabled", keyTrustCheckEnabled );
    }


    // Peer Settings


    public static String getPublicUrl()
    {
        return PROPERTIES.getString( "publicURL", DEFAULT_PUBLIC_URL );
    }


    public static void setPublicUrl( String publicUrl ) throws ConfigurationException
    {
        validatePublicUrl( publicUrl );
        saveProperty( "publicURL", publicUrl );
    }


    public static String getPeerSecretKeyringPwd()
    {
        return PROPERTIES.getString( "peerSecretKeyringPwd", DEFAULT_PEER_PWD );
    }


    public static void setPeerSecretKeyringPwd( String pwd ) throws ConfigurationException
    {
        validatePublicUrl( pwd );
        saveProperty( "peerSecretKeyringPwd", pwd );
    }


    protected static void saveProperty( final String name, final Object value )
    {
        try
        {
            PROPERTIES.setProperty( name, value );
            PROPERTIES.save();
        }
        catch ( ConfigurationException e )
        {
            LOG.error( "Error in saving subutaisettings.cfg file.", e );
        }
    }
}

