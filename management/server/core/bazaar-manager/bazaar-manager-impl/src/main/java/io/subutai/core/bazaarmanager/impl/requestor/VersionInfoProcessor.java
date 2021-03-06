package io.subutai.core.bazaarmanager.impl.requestor;


import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.subutai.bazaar.share.dto.PeerInfoDto;
import io.subutai.bazaar.share.dto.RhVersionInfoDto;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.settings.SubutaiInfo;
import io.subutai.core.bazaarmanager.api.BazaarRequester;
import io.subutai.core.bazaarmanager.api.RestClient;
import io.subutai.core.bazaarmanager.api.RestResult;
import io.subutai.core.bazaarmanager.api.exception.BazaarManagerException;
import io.subutai.core.bazaarmanager.impl.BazaarManagerImpl;
import io.subutai.core.bazaarmanager.impl.ConfigManager;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.bazaar.share.dto.VersionInfoDto;

import static java.lang.String.format;


public class VersionInfoProcessor extends BazaarRequester
{
    private static final Logger LOG = LoggerFactory.getLogger( VersionInfoProcessor.class );

    private static final Map<String, Object> VERSION_CACHE = Maps.newConcurrentMap();
    private static final String KEY_SSV = "ss_version";
    private static final String KEY_RHV = "rh_version";
    private static final String KEY_P2PV = "p2p_version";
    private static final String KEY_DATE = "update_date";


    private ConfigManager configManager;

    private PeerManager peerManager;


    public VersionInfoProcessor( final BazaarManagerImpl bazaarManager, final PeerManager peerManager,
                                 final ConfigManager configManager, final RestClient restClient )
    {
        super( bazaarManager, restClient );

        this.peerManager = peerManager;
        this.configManager = configManager;
    }


    @Override
    public void request() throws BazaarManagerException
    {
        sendVersionInfo();
        sendPeerInfo();
    }


    @Deprecated
    private void sendVersionInfo() throws BazaarManagerException
    {
        String path = format( "/rest/v1/peers/%s/version-info", peerManager.getLocalPeer().getId() );


        String ssV = "", rhV = "", p2pV = "";

        try
        {
            ResourceHost host = configManager.getPeerManager().getLocalPeer().getManagementHost();

            ssV = SubutaiInfo.getVersion();
            rhV = host.getRhVersion().replace( "Subutai version", "" ).trim();
            p2pV = host.getP2pVersion().replace( "p2p Cloud project", "" ).trim();
        }
        catch ( Exception e )
        {
            LOG.error( "Error getting system info: {}", e.getMessage() );
        }

        if ( areVersionsChanged( ssV, rhV, p2pV ) )
        {
            VersionInfoDto versionInfoDto = new VersionInfoDto();

            versionInfoDto.setPeerId( configManager.getPeerId() );
            versionInfoDto.setSsVersion( ssV );
            versionInfoDto.setRhVersion( rhV );
            versionInfoDto.setP2pVersion( p2pV );
            versionInfoDto.setBuildTime( SubutaiInfo.getBuildTime() );
            versionInfoDto.setBranch( SubutaiInfo.getBranch() );
            versionInfoDto.setCommitId( SubutaiInfo.getCommitId() );

            RestResult<Object> restResult = restClient.post( path, versionInfoDto );

            checkRestResultAndSetChangedVersion( restResult, ssV, rhV, p2pV );
        }
    }


    private void sendPeerInfo() throws BazaarManagerException
    {
        String path = format( "/rest/v1/peers/%s/info", peerManager.getLocalPeer().getId() );

        try
        {
            Set<RhVersionInfoDto> rhVersions = Sets.newHashSet();
            boolean wasChanged = false;
            PeerInfoDto infoDto = null;
            String ssV = "", rhV = "", p2pV = "";

            for ( ResourceHost rh : peerManager.getLocalPeer().getResourceHosts() )
            {
                RhVersionInfoDto rhDto = new RhVersionInfoDto();

                ssV = SubutaiInfo.getVersion();
                rhV = rh.getRhVersion();
                p2pV = rh.getP2pVersion();

                rhDto.setRhId( rh.getId() );
                rhDto.setManagement( rh.isManagementHost() );
                rhDto.setSsVersion( ssV );
                rhDto.setP2pVersion( p2pV );
                rhDto.setRhVersion( rhV );

                rhVersions.add( rhDto );

                if ( areVersionsChanged( ssV, rhV, p2pV ) )
                {
                    wasChanged = true;

                    infoDto = new PeerInfoDto();

                    infoDto.setId( configManager.getPeerId() );
                    infoDto.setVersion( SubutaiInfo.getVersion() );
                    infoDto.setBuildTime( SubutaiInfo.getBuildTime() );
                    infoDto.setBranch( SubutaiInfo.getBranch() );
                    infoDto.setCommitId( SubutaiInfo.getCommitId() );

                    infoDto.setRhVersionInfoDtoList( rhVersions );
                }
            }

            if ( wasChanged )
            {
                RestResult<Object> restResult = restClient.post( path, infoDto );
                checkRestResultAndSetChangedVersion( restResult, ssV, rhV, p2pV );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Error getting system info: {}", e.getMessage() );
        }
    }


    private void checkRestResultAndSetChangedVersion( RestResult<Object> restResult, String subutaiVersion,
                                                      String rhVersion, String p2pVersion )
            throws BazaarManagerException
    {
        if ( restResult.isSuccess() )
        {
            if ( restResult.getEntity() != null && restResult.getEntity() instanceof String && StringUtils
                    .isNotBlank( ( String ) restResult.getEntity() ) )
            {
                ( ( BazaarManagerImpl ) bazaarManager ).setPeerName( ( String ) restResult.getEntity() );
                setChangedVersions( subutaiVersion, rhVersion, p2pVersion );
            }
        }
        else
        {
            throw new BazaarManagerException( "Error on sending version info to Bazaar: " + restResult.getError() );
        }
    }


    private boolean areVersionsChanged( String ssV, String rhV, String p2pV )
    {
        if ( VERSION_CACHE.get( KEY_SSV ) == null || !VERSION_CACHE.get( KEY_SSV ).equals( ssV ) )
        {
            return true;
        }


        if ( VERSION_CACHE.get( KEY_RHV ) == null || !VERSION_CACHE.get( KEY_RHV ).equals( rhV ) )
        {
            return true;
        }


        if ( VERSION_CACHE.get( KEY_P2PV ) == null || !VERSION_CACHE.get( KEY_P2PV ).equals( p2pV ) )
        {
            return true;
        }

        //by force updates every 15 minutes
        if ( VERSION_CACHE.get( KEY_DATE ) != null )
        {
            try
            {
                Date lastUpdate = ( Date ) VERSION_CACHE.get( KEY_DATE );

                Calendar cal = Calendar.getInstance();
                cal.setTime( new Date() );
                cal.add( Calendar.MINUTE, -15 );
                Date sendTimeout = cal.getTime();

                if ( lastUpdate.before( sendTimeout ) )
                {
                    return true;
                }
            }
            catch ( Exception e )
            {
                LOG.error( e.getMessage() );
            }
        }
        else
        {
            return true;
        }

        return false;
    }


    private void setChangedVersions( String ssV, String rhV, String p2pV )
    {
        VERSION_CACHE.put( KEY_SSV, ssV );
        VERSION_CACHE.put( KEY_RHV, rhV );
        VERSION_CACHE.put( KEY_P2PV, p2pV );
        VERSION_CACHE.put( KEY_DATE, new Date() );
    }
}
