package io.subutai.core.hubmanager.impl.appscale;


import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.HttpStatus;

import com.google.common.collect.Sets;

import io.subutai.core.hubmanager.api.StateLinkProccessor;
import io.subutai.core.hubmanager.impl.ConfigManager;
import io.subutai.hub.share.dto.AppScaleConfigDto;
import io.subutai.hub.share.json.JsonUtil;


public class AppScaleProcessor implements StateLinkProccessor
{
    private final Logger log = LoggerFactory.getLogger( getClass() );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Set<String> processLinks = Sets.newConcurrentHashSet();

    private final ConfigManager configManager;

    private final AppScaleManager appScaleManager;


    public AppScaleProcessor( ConfigManager configManager, AppScaleManager appScaleManager )
    {
        this.configManager = configManager;
        this.appScaleManager = appScaleManager;
    }


    @Override
    public void processStateLinks( final Set<String> stateLinks )
    {
        for ( String stateLink : stateLinks )
        {
            processLink( stateLink );
        }
    }


    private void processLink( final String stateLink )
    {
        if ( !stateLink.contains( "appscale" ) )
        {
            return;
        }

        if ( processLinks.contains( stateLink ) )
        {
            log.debug( "AppScale installation for this link is in progress" );

            return;
        }

        final AppScaleConfigDto config = getData( stateLink );

        log.debug( "config: {}", config );

        if ( config == null )
        {
            return;
        }

        executor.execute( new Runnable()
        {
            public void run()
            {
                processLinks.add( stateLink );

//                appScaleManager.installCluster( config );

                try
                {
                    Thread.sleep( 10000 );
                }
                catch ( InterruptedException e )
                {
                    e.printStackTrace();
                }
                ;

                update( stateLink );

                processLinks.remove( stateLink );
            }
        } );

        executor.shutdown();
    }


    private void update( String link )
    {
        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( link, configManager.getHubIp() );

            Response res = client.post( null );

            log.debug( "Response: HTTP {} - {}", res.getStatus(), res.getStatusInfo().getReasonPhrase() );

        }
        catch ( Exception e )
        {
            log.error( "Error to update AppScale data to Hub: ", e );
        }
    }


    private AppScaleConfigDto getData( String link )
    {
        log.debug( "Getting AppScale data from Hub: {}", link );

        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( link, configManager.getHubIp() );

            Response res = client.get();

            log.debug( "Response: HTTP {} - {}", res.getStatus(), res.getStatusInfo().getReasonPhrase() );

            if ( res.getStatus() != HttpStatus.SC_OK )
            {
                log.error( "Error to get AppScale data from Hub: HTTP {} - {}", res.getStatus(), res.getStatusInfo().getReasonPhrase() );

                return null;
            }

            byte[] encryptedContent = configManager.readContent( res );

            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );

            return JsonUtil.fromCbor( plainContent, AppScaleConfigDto.class );
        }
        catch ( Exception e )
        {
            log.error( "Error to get AppScale data from Hub: ", e );

            return null;
        }
    }

}
