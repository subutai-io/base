package io.subutai.core.hubmanager.impl.processor;


import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.ArrayUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.HttpStatus;

import io.subutai.core.appender.SubutaiErrorEvent;
import io.subutai.core.appender.SubutaiErrorEventListener;
import io.subutai.core.hubmanager.impl.ConfigManager;
import io.subutai.core.hubmanager.impl.IntegrationImpl;
import io.subutai.hub.share.dto.SystemLogsDto;
import io.subutai.hub.share.json.JsonUtil;


public class HubLoggerProcessor implements Runnable, SubutaiErrorEventListener
{
    private static final Logger LOG = LoggerFactory.getLogger( HubLoggerProcessor.class.getName() );
    private ConfigManager configManager;
    private IntegrationImpl manager;
    private final Map<String, String> errLogs = new LinkedHashMap<>();


    public HubLoggerProcessor( final ConfigManager configManager, final IntegrationImpl integration )
    {
        this.configManager = configManager;
        this.manager = integration;
    }


    public HubLoggerProcessor()
    {
    }


    @Override
    public void run()
    {
        Set<String> logs = new HashSet<>();

        synchronized ( errLogs )
        {
            logs.addAll( errLogs.values() );
            errLogs.clear();
        }

        if ( !logs.isEmpty() && manager.getRegistrationState() )
        {
            WebClient client = null;
            try
            {
                client = configManager.getTrustedWebClientWithAuth( "/rest/v1/system-bugs", configManager.getHubIp() );

                SystemLogsDto logsDto = new SystemLogsDto();
                logsDto.setLogs( logs );

                byte[] plainData = JsonUtil.toCbor( logsDto );
                byte[] encryptedData = configManager.getMessenger().produce( plainData );

                LOG.debug( "Sending System logs to HUB:" );

                Response r = client.post( encryptedData );

                if ( r.getStatus() != HttpStatus.SC_NO_CONTENT )
                {
                    LOG.warn( "Could not send logs to Hub {}", r.readEntity( String.class ) );
                }
                else
                {

                    LOG.debug( "System logs sent to HUB successfully." );
                }

                r.close();
            }
            catch ( Exception e )
            {
                LOG.warn( "Could not send logs to Hub {}", e.getMessage() );
            }
            finally
            {
                if ( client != null )
                {
                    client.close();
                }
            }
        }
    }


    @Override
    public void onEvent( final SubutaiErrorEvent event )
    {
        LOG.info( String.format( "RECEIVED:%n:%s", event.toString() ) );

        try
        {
            byte[] loggerName = event.getLoggerName().getBytes();
            byte[] renderedMsg = event.getRenderedMessage().getBytes();
            byte[] combined = ArrayUtils.addAll( loggerName, renderedMsg );
            MessageDigest md = MessageDigest.getInstance( "MD5" );
            byte[] theDigest = md.digest( combined );
            String key = new String( theDigest );

            synchronized ( errLogs )
            {
                errLogs.put( key, event.toString() );

                while ( errLogs.size() > 10 )
                {
                    //delete oldest value
                    errLogs.remove( errLogs.keySet().iterator().next() );
                }
            }
        }
        catch ( Exception e )
        {
            LOG.warn( "Error in #onEvent {}", e.getMessage() );
        }
    }
}
