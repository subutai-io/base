package io.subutai.core.kurjun.impl;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Hex;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Injector;

import ai.subut.kurjun.ar.CompressionType;
import ai.subut.kurjun.cfparser.ControlFileParserModule;
import ai.subut.kurjun.common.KurjunBootstrap;
import ai.subut.kurjun.common.service.KurjunContext;
import ai.subut.kurjun.common.service.KurjunProperties;
import ai.subut.kurjun.common.utils.InetUtils;
import ai.subut.kurjun.index.PackagesIndexParserModule;
import ai.subut.kurjun.metadata.common.DefaultMetadata;
import ai.subut.kurjun.metadata.common.subutai.DefaultTemplate;
import ai.subut.kurjun.metadata.factory.PackageMetadataStoreFactory;
import ai.subut.kurjun.metadata.factory.PackageMetadataStoreModule;
import ai.subut.kurjun.model.metadata.Metadata;
import ai.subut.kurjun.model.metadata.SerializableMetadata;
import ai.subut.kurjun.model.repository.LocalRepository;
import ai.subut.kurjun.model.repository.NonLocalRepository;
import ai.subut.kurjun.model.repository.Repository;
import ai.subut.kurjun.model.repository.UnifiedRepository;
import ai.subut.kurjun.model.security.Identity;
import ai.subut.kurjun.model.security.Permission;
import ai.subut.kurjun.quota.DataUnit;
import ai.subut.kurjun.quota.QuotaException;
import ai.subut.kurjun.quota.QuotaInfoStore;
import ai.subut.kurjun.quota.QuotaManagementModule;
import ai.subut.kurjun.quota.QuotaManagerFactory;
import ai.subut.kurjun.quota.disk.DiskQuota;
import ai.subut.kurjun.quota.disk.DiskQuotaManager;
import ai.subut.kurjun.quota.transfer.TransferQuota;
import ai.subut.kurjun.quota.transfer.TransferQuotaManager;
import ai.subut.kurjun.repo.RepositoryFactory;
import ai.subut.kurjun.repo.RepositoryModule;
import ai.subut.kurjun.riparser.ReleaseIndexParserModule;
import ai.subut.kurjun.security.SecurityModule;
import ai.subut.kurjun.security.service.AuthManager;
import ai.subut.kurjun.snap.SnapMetadataParserModule;
import ai.subut.kurjun.storage.factory.FileStoreFactory;
import ai.subut.kurjun.storage.factory.FileStoreModule;
import ai.subut.kurjun.subutai.SubutaiTemplateParserModule;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.settings.Common;
import io.subutai.common.settings.KurjunSettings;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.kurjun.api.KurjunTransferQuota;
import io.subutai.core.kurjun.api.TemplateManager;


@PermitAll
public class TemplateManagerImpl implements TemplateManager
{

    private static final Logger LOGGER = LoggerFactory.getLogger( TemplateManagerImpl.class );

    private static final Set<KurjunContext> CONTEXTS = new HashSet<>();

    // url list read from kurjun.cfg file on bundle start up
    private final List<String> globalKurjunUrlList = new ArrayList<>();

    private Injector injector;

    private AuthManager authManager;

    private final io.subutai.core.security.api.SecurityManager securityManager;

    private ai.subut.kurjun.security.service.IdentityManager kurjunIdentityManager;

    private Set<RepoUrl> remoteRepoUrls = new HashSet<>();

    private Set<RepoUrl> globalRepoUrls = new LinkedHashSet<>();

    private final LocalPeer localPeer;

    private final IdentityManager subutaiIdentityManager;

    private final RepoUrlStore repoUrlStore = new RepoUrlStore( Common.SUBUTAI_APP_DATA_PATH );

    private ScheduledExecutorService metadataCacheUpdater;


    public TemplateManagerImpl( LocalPeer localPeer, IdentityManager identityManager,
                                io.subutai.core.security.api.SecurityManager securityManager, String globalKurjunUrl )
    {
        this.localPeer = localPeer;
        this.subutaiIdentityManager = identityManager;
        this.securityManager = securityManager;
        parseGlobalKurjunUrls( globalKurjunUrl );
    }


    public void init()
    {
        injector = bootstrapDI();

        // init repo urls
        try
        {
            // Load remote repo urls from store
            remoteRepoUrls = repoUrlStore.getRemoteTemplateUrls();

            // Refresh global urls
            repoUrlStore.removeAllGlobalTemplateUrl();
            for ( String url : KurjunSettings.getGlobalKurjunUrls() )
            {
                repoUrlStore.addGlobalTemplateUrl( new RepoUrl( new URL( url ), null ) );
            }

            // Load global repo urls from store
            globalRepoUrls = repoUrlStore.getGlobalTemplateUrls();
        }
        catch ( IOException e )
        {
            LOGGER.error( "Failed to get remote repo urls", e );
        }

        logAllUrlsInUse();

        kurjunIdentityManager = injector.getInstance( ai.subut.kurjun.security.service.IdentityManager.class );

        authManager = injector.getInstance( AuthManager.class );

        // init contexts
        KurjunProperties properties = injector.getInstance( KurjunProperties.class );
        setContexts( properties );

        // schedule metadata cache updater
        metadataCacheUpdater = Executors.newSingleThreadScheduledExecutor();
        metadataCacheUpdater.scheduleWithFixedDelay( () -> refreshMetadataCache(), 5, 30, TimeUnit.SECONDS );
    }


    public void dispose()
    {
        metadataCacheUpdater.shutdown();
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public TemplateKurjun getTemplate( String context, byte[] md5, boolean isKurjunClient ) throws IOException
    {

        checkPermission( context, Permission.GET_PACKAGE, Hex.encodeHexString( md5 ) );

        DefaultMetadata m = new DefaultMetadata();
        m.setMd5sum( md5 );

        UnifiedRepository repo = getRepository( context, isKurjunClient );
        DefaultTemplate meta = ( DefaultTemplate ) repo.getPackageInfo( m );
        if ( meta != null )
        {
            TemplateKurjun template =
                    new TemplateKurjun( Hex.encodeHexString( meta.getMd5Sum() ), meta.getName(), meta.getVersion(),
                            meta.getArchitecture().name(), meta.getParent(), meta.getPackage() );
            template.setConfigContents( meta.getConfigContents() );
            template.setPackagesContents( meta.getPackagesContents() );
            return template;
        }
        return null;
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public TemplateKurjun getTemplate( String context, String name, String version, boolean isKurjunClient )
            throws IOException
    {
        DefaultMetadata m = new DefaultMetadata();
        m.setName( name );
        m.setVersion( version );

        UnifiedRepository repo = getRepository( context, isKurjunClient );

        DefaultTemplate meta = ( DefaultTemplate ) repo.getPackageInfo( m );
        if ( meta != null )
        {
            checkPermission( context, Permission.GET_PACKAGE, Hex.encodeHexString( meta.getMd5Sum() ) );

            TemplateKurjun template =
                    new TemplateKurjun( Hex.encodeHexString( meta.getMd5Sum() ), meta.getName(), meta.getVersion(),
                            meta.getArchitecture().name(), meta.getParent(), meta.getPackage() );
            template.setConfigContents( meta.getConfigContents() );
            template.setPackagesContents( meta.getPackagesContents() );
            return template;
        }
        return null;
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public TemplateKurjun getTemplate( final String name )
    {
        try
        {
            return getTemplate( PUBLIC_REPO, name, null, false );
        }
        catch ( IOException e )
        {
            LOGGER.error( "Error in getTemplate(name)", e );

            return null;
        }
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public InputStream getTemplateData( String context, byte[] md5, boolean isKurjunClient ) throws IOException
    {
        checkPermission( context, Permission.GET_PACKAGE, Hex.encodeHexString( md5 ) );

        DefaultMetadata m = new DefaultMetadata();
        m.setMd5sum( md5 );

        UnifiedRepository repo = getRepository( context, isKurjunClient );
        InputStream is = repo.getPackageStream( m );

        if ( is != null )
        {
            QuotaManagerFactory quotaManagerFactory = injector.getInstance( QuotaManagerFactory.class );
            TransferQuotaManager qm = quotaManagerFactory.createTransferQuotaManager( new KurjunContext( context ) );
            return qm.createManagedStream( is );
        }
        return null;
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public List<TemplateKurjun> list( String context, boolean isKurjunClient ) throws IOException
    {
        UnifiedRepository repo = getRepository( context, isKurjunClient );
        Set<SerializableMetadata> metadatas = listPackagesFromCache( repo );

        List<TemplateKurjun> result = new LinkedList<>();
        for ( SerializableMetadata metadata : metadatas )
        {
            if ( !isAllowed( context, Permission.GET_PACKAGE, Hex.encodeHexString( metadata.getMd5Sum() ) ) )
            {
                continue;
            }

            DefaultTemplate meta = ( DefaultTemplate ) metadata;
            TemplateKurjun t =
                    new TemplateKurjun( Hex.encodeHexString( meta.getMd5Sum() ), meta.getName(), meta.getVersion(),
                            meta.getArchitecture().name(), meta.getParent(), meta.getPackage() );
            t.setConfigContents( meta.getConfigContents() );
            t.setPackagesContents( meta.getPackagesContents() );
            result.add( t );
        }
        return result;
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public List<Map<String, Object>> listAsSimple( String context ) throws IOException
    {
        List<Map<String, Object>> simpleList = new ArrayList<>();

        LocalRepository localRepo = getLocalRepository( context );

        List<SerializableMetadata> localList = localRepo.listPackages();

        for ( SerializableMetadata metadata : localList )
        {
            if ( !isAllowed( context, Permission.GET_PACKAGE, Hex.encodeHexString( metadata.getMd5Sum() ) ) )
            {
                continue;
            }

            simpleList.add( convertToSimple( ( DefaultTemplate ) metadata, true ) );
        }

        UnifiedRepository unifiedRepo = getRepository( context, false );

        List<SerializableMetadata> unifiedList = unifiedRepo.listPackages();

        unifiedList.removeAll( localList );

        for ( SerializableMetadata metadata : unifiedList )
        {
            if ( !isAllowed( context, Permission.GET_PACKAGE, Hex.encodeHexString( metadata.getMd5Sum() ) ) )
            {
                continue;
            }

            simpleList.add( convertToSimple( ( DefaultTemplate ) metadata, false ) );
        }
        return simpleList;
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public List<TemplateKurjun> list()
    {
        try
        {
            return list( PUBLIC_REPO, false );
        }
        catch ( IOException e )
        {
            LOGGER.error( "Error in list", e );
            return Lists.newArrayList();
        }
    }


    @Override
    public boolean isUploadAllowed( String context )
    {
        return isAllowed( context, Permission.ADD_PACKAGE, "*" );
    }


    @Override
    @RolesAllowed( "Template-Management|Write" )
    public byte[] upload( String context, InputStream inputStream ) throws IOException
    {

        checkPermission( context, Permission.ADD_PACKAGE, "*" );

        LocalRepository repo = getLocalRepository( context );
        QuotaManagerFactory quotaManagerFactory = injector.getInstance( QuotaManagerFactory.class );
        DiskQuotaManager diskQuotaManager = quotaManagerFactory.createDiskQuotaManager( new KurjunContext( context ) );

        Path dump = null;
        try
        {
            dump = diskQuotaManager.copyStream( inputStream );
        }
        catch ( QuotaException ex )
        {
            throw new IOException( ex );
        }

        try ( InputStream is = new FileInputStream( dump.toFile() ) )
        {
            Metadata m = repo.put( is, CompressionType.GZIP );

            if ( !PUBLIC_REPO.equals( context ) )
            {
                String keyId = subutaiIdentityManager.getActiveUser().getSecurityKeyId();
                String fprint = securityManager.getKeyManager().getKeyData( keyId ).getPublicKeyFingerprint();

                Identity iden = kurjunIdentityManager.getIdentity( fprint );
                String md5 = Hex.encodeHexString( m.getMd5Sum() );
                kurjunIdentityManager.addResourcePermission( Permission.GET_PACKAGE, iden, md5 );
                kurjunIdentityManager.addResourcePermission( Permission.DEL_PACKAGE, iden, md5 );
            }
            return m.getMd5Sum();
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to put template", ex );
        }
        finally
        {
            dump.toFile().delete();
        }
        return null;
    }


    @Override
    @RolesAllowed( "Template-Management|Delete" )
    public boolean delete( String context, byte[] md5 ) throws IOException
    {
        checkPermission( context, Permission.DEL_PACKAGE, Hex.encodeHexString( md5 ) );

        LocalRepository repo = getLocalRepository( context );
        try
        {
            repo.delete( md5 );
            return true;
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to delete template", ex );
            return false;
        }
    }


    @Override
    public List<Map<String, Object>> getRemoteRepoUrls()
    {
        List<Map<String, Object>> urls = new ArrayList<>();
        try
        {
            for ( RepoUrl r : repoUrlStore.getRemoteTemplateUrls() )
            {
                Map<String, Object> map = new HashMap<>( 3 );
                map.put( "url", r.getUrl().toExternalForm() );
                map.put( "useToken", r.getToken() != null ? "yes" : "no" );
                map.put( "global", "no" );
                urls.add( map );
            }

            for ( RepoUrl r : repoUrlStore.getGlobalTemplateUrls() )
            {
                Map<String, Object> map = new HashMap<>( 3 );
                map.put( "url", r.getUrl().toExternalForm() );
                map.put( "useToken", r.getToken() != null ? "yes" : "no" );
                map.put( "global", "yes" );
                urls.add( map );
            }
        }
        catch ( IOException e )
        {
            LOGGER.error( "", e );
        }
        return urls;
    }


    @Override
    @RolesAllowed( "Template-Management|Write" )
    public void addRemoteRepository( URL url, String token )
    {
        try
        {
            if ( url != null && !url.getHost().equals( getExternalIp() ) )
            {
                repoUrlStore.addRemoteTemplateUrl( new RepoUrl( url, token ) );
                remoteRepoUrls = repoUrlStore.getRemoteTemplateUrls();
                LOGGER.info( "Remote template host url is added: {}", url );
            }
            else
            {
                LOGGER.error( "Failed to add remote host url: {}", url );
            }
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to add remote host url: {}", url, ex );
        }
    }


    @Override
    @RolesAllowed( "Template-Management|Delete" )
    public void removeRemoteRepository( URL url )
    {
        if ( url != null )
        {
            try
            {
                RepoUrl r = repoUrlStore.removeRemoteTemplateUrl( new RepoUrl( url, null ) );
                if ( r != null )
                {
                    LOGGER.info( "Remote template host url is removed: {}", url );
                }
                else
                {
                    LOGGER.warn( "Failed to remove remote host url: {}. Either it does not exist or it is a global url",
                            url );
                }
                remoteRepoUrls = repoUrlStore.getRemoteTemplateUrls();
            }
            catch ( IOException e )
            {
                LOGGER.error( "Failed to remove remote host url: {}", url, e );
            }
        }
    }


    @Override
    @RolesAllowed( "Template-Management|Read" )
    public Set<String> getContexts()
    {
        return Collections.unmodifiableSet( CONTEXTS.stream().map( c -> c.getName() ).collect( Collectors.toSet() ) );
    }


    @Override
    public Long getDiskQuota( String context )
    {
        KurjunContext c = getContext( context );
        QuotaInfoStore quotaInfoStore = injector.getInstance( QuotaInfoStore.class );
        try
        {
            DiskQuota diskQuota = quotaInfoStore.getDiskQuota( c );
            if ( diskQuota != null )
            {
                return diskQuota.getThreshold() * diskQuota.getUnit().toBytes() / DataUnit.MB.toBytes();
            }
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to get disk quota", ex );
        }
        return null;
    }


    @Override
    public boolean setDiskQuota( long size, String context )
    {
        KurjunContext kurjunContext = getContext( context );

        DiskQuota diskQuota = new DiskQuota( size, DataUnit.MB );
        QuotaInfoStore quotaInfoStore = injector.getInstance( QuotaInfoStore.class );
        try
        {
            quotaInfoStore.saveDiskQuota( diskQuota, kurjunContext );
            return true;
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to save disk quota", ex );
            return false;
        }
    }


    @Override
    public KurjunTransferQuota getTransferQuota( String context )
    {
        KurjunContext c = getContext( context );
        QuotaInfoStore quotaInfoStore = injector.getInstance( QuotaInfoStore.class );
        try
        {
            TransferQuota q = quotaInfoStore.getTransferQuota( c );
            if ( q != null )
            {
                return new KurjunTransferQuota( q.getThreshold(), q.getTime(), q.getTimeUnit() );
            }
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to get disk quota", ex );
        }
        return null;
    }


    @Override
    public boolean setTransferQuota( KurjunTransferQuota quota, String context )
    {
        KurjunContext kurjunContext = getContext( context );

        TransferQuota transferQuota = new TransferQuota();
        transferQuota.setThreshold( quota.getThreshold() );
        transferQuota.setUnit( DataUnit.MB );
        transferQuota.setTime( quota.getTimeFrame() );
        transferQuota.setTimeUnit( quota.getTimeUnit() );

        QuotaInfoStore quotaInfoStore = injector.getInstance( QuotaInfoStore.class );
        try
        {
            quotaInfoStore.saveTransferQuota( transferQuota, kurjunContext );
            return true;
        }
        catch ( IOException ex )
        {
            LOGGER.error( "Failed to save transfer quota", ex );
            return false;
        }
    }


    private String getExternalIp()
    {
        try
        {
            if ( localPeer != null )
            {
                return localPeer.getExternalIp();
            }
            else
            {
                List<InetAddress> ips = InetUtils.getLocalIPAddresses();
                return ips.get( 0 ).getHostAddress();
            }
        }
        catch ( PeerException | SocketException | IndexOutOfBoundsException ex )
        {
            LOGGER.error( "Cannot get external ip. Returning null.", ex );
            return null;
        }
    }


    private Injector bootstrapDI()
    {
        KurjunBootstrap bootstrap = new KurjunBootstrap();
        bootstrap.addModule( new ControlFileParserModule() );
        bootstrap.addModule( new ReleaseIndexParserModule() );
        bootstrap.addModule( new PackagesIndexParserModule() );
        bootstrap.addModule( new SubutaiTemplateParserModule() );

        bootstrap.addModule( new FileStoreModule() );
        bootstrap.addModule( new PackageMetadataStoreModule() );
        bootstrap.addModule( new SnapMetadataParserModule() );

        bootstrap.addModule( new RepositoryModule() );
        bootstrap.addModule( new TrustedWebClientFactoryModule() );
        bootstrap.addModule( new SecurityModule() );
        bootstrap.addModule( new QuotaManagementModule() );

        bootstrap.boot();

        return bootstrap.getInjector();
    }


    private LocalRepository getLocalRepository( String context ) throws IOException
    {
        try
        {
            KurjunContext c = getContext( context );
            RepositoryFactory repositoryFactory = injector.getInstance( RepositoryFactory.class );
            return repositoryFactory.createLocalTemplate( c );
        }
        catch ( IllegalArgumentException ex )
        {
            throw new IOException( ex );
        }
    }


    private UnifiedRepository getRepository( String context, boolean isKurjunClient ) throws IOException
    {
        RepositoryFactory repositoryFactory = injector.getInstance( RepositoryFactory.class );
        UnifiedRepository unifiedRepo = repositoryFactory.createUnifiedRepo();
        unifiedRepo.getRepositories().add( getLocalRepository( context ) );

        if ( !isKurjunClient )
        {
            for ( RepoUrl repoUrl : remoteRepoUrls )
            {
                unifiedRepo.getRepositories().add( repositoryFactory
                        .createNonLocalTemplate( repoUrl.getUrl().toString(), null, repoUrl.getToken() ) );
            }

            // shuffle the global repo list to randomize and normalize usage of them
            List<RepoUrl> list = new ArrayList<>( globalRepoUrls );
            Collections.shuffle( list );

            for ( RepoUrl repoUrl : list )
            {
                unifiedRepo.getSecondaryRepositories().add( repositoryFactory
                        .createNonLocalTemplate( repoUrl.getUrl().toString(), null, repoUrl.getToken() ) );
            }
        }
        return unifiedRepo;
    }


    private static void setContexts( KurjunProperties properties )
    {
        // init template type contexts
        CONTEXTS.add( new KurjunContext( "public" ) );
        CONTEXTS.add( new KurjunContext( "trust" ) );

        for ( KurjunContext kc : CONTEXTS )
        {
            Properties kcp = properties.getContextProperties( kc );
            kcp.setProperty( FileStoreFactory.TYPE, FileStoreFactory.FILE_SYSTEM );
            kcp.setProperty( PackageMetadataStoreModule.PACKAGE_METADATA_STORE_TYPE,
                             PackageMetadataStoreFactory.FILE_DB );
        }
    }


    /**
     * Gets Kurjun context for templates repository type.
     *
     * @return context instance
     * @throws IllegalArgumentException if invalid/unknown context value is supplied
     */
    private KurjunContext getContext( String context )
    {
        Set<KurjunContext> set = CONTEXTS;
        for ( KurjunContext c : set )
        {
            if ( c.getName().equals( context ) )
            {
                return c;
            }
        }
        throw new IllegalArgumentException( "Invalid context" );
    }


    private void checkPermission( String context, Permission permission, String resource ) throws AccessControlException
    {
        if ( !isAllowed( context, permission, resource ) )
        {
            throw new AccessControlException(
                    String.format( "Action denied for resource %s with permission %s of identity %s", resource,
                            permission, getActiveUserFingerprint() ) );
        }
    }


    private boolean isAllowed( String context, Permission permission, String resource )
    {
        if ( !PUBLIC_REPO.equals( context ) )
        {
            if ( authManager != null )
            {
                return authManager.isAllowed( getActiveUserFingerprint(), permission, resource );
            }
            // authManager must exist, otherwise don't allow
            return false;
        }
        return true;
    }


    private String getActiveUserFingerprint()
    {
        String keyId = subutaiIdentityManager.getActiveUser().getSecurityKeyId();
        return securityManager.getKeyManager().getKeyData( keyId ).getPublicKeyFingerprint();
    }


    private void parseGlobalKurjunUrls( String globalKurjunUrl )
    {
        if ( !Strings.isNullOrEmpty( globalKurjunUrl ) )
        {
            String urls[] = globalKurjunUrl.split( "," );

            for ( int x = 0; x < urls.length; x++ )
            {
                urls[x] = urls[x].trim();
                globalKurjunUrlList.add( urls[x] );
            }
        }
    }


    private void logAllUrlsInUse()
    {
        LOGGER.info( "Remote template urls:" );
        for ( RepoUrl r : remoteRepoUrls )
        {
            LOGGER.info( r.toString() );
        }

        for ( RepoUrl r : globalRepoUrls )
        {
            LOGGER.info( r.toString() );
        }
    }


    private Map<String, Object> convertToSimple( DefaultTemplate template, boolean deletable )
    {
        Map<String, Object> simple = new HashMap<>();
        simple.put( "name", template.getName() );
        simple.put( "md5Sum", Hex.encodeHexString( template.getMd5Sum() ) );
        simple.put( "parent", template.getParent() );
        simple.put( "architecture", template.getArchitecture() );
        simple.put( "version", template.getVersion() );
        simple.put( "deletable", deletable );

        return simple;
    }


    /**
     * Gets cached metadata from the repositories of the supplied unified repository.
     */
    private Set<SerializableMetadata> listPackagesFromCache( UnifiedRepository repository )
    {
        Set<SerializableMetadata> result = new HashSet<>();

        Set<Repository> repos = new HashSet<>();
        repos.addAll( repository.getRepositories() );
        repos.addAll( repository.getSecondaryRepositories() );

        for ( Repository repo : repos )
        {
            if ( repo instanceof NonLocalRepository )
            {
                NonLocalRepository remote = ( NonLocalRepository ) repo;
                List<SerializableMetadata> ls = remote.getMetadataCache().getMetadataList();
                result.addAll( ls );
            }
            else
            {
                List<SerializableMetadata> ls = repo.listPackages();
                result.addAll( ls );
            }
        }
        return result;
    }


    /**
     * Refreshes metadata cache for each remote repository.
     */
    private void refreshMetadataCache()
    {
        Set<NonLocalRepository> remotes = new HashSet<>();
        RepositoryFactory repoFactory = injector.getInstance( RepositoryFactory.class );

        for ( RepoUrl url : remoteRepoUrls )
        {
            remotes.add( repoFactory.createNonLocalTemplate( url.getUrl().toString(), null, url.getToken() ) );
        }
        for ( RepoUrl url : globalRepoUrls )
        {
            remotes.add( repoFactory.createNonLocalTemplate( url.getUrl().toString(), null, url.getToken() ) );
        }

        for ( NonLocalRepository remote : remotes )
        {
            remote.getMetadataCache().refresh();
        }
    }


}
