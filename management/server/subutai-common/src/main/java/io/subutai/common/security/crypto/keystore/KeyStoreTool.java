package io.subutai.common.security.crypto.keystore;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.security.crypto.certificate.CertificateTool;
import io.subutai.common.security.utils.io.SafeCloseUtil;


/**
 * Main Class for keystore management. Manages Keystores and Truststores
 */
public class KeyStoreTool
{

    private static final Logger LOGGER = LoggerFactory.getLogger( KeyStoreTool.class );
    private FileInputStream finStream = null;
    private FileOutputStream foutStream = null;
    private CertificateTool certificateTool = new CertificateTool();


    /**
     * KeyStoreManager constructor
     */
    public KeyStoreTool()
    {

    }


    /**
     * *********************************************************************************** Load keystore and create
     * KeyStore object
     *
     * @param keyStoreData KeyStoreData
     *
     * @return KeyStore
     */
    public KeyStore load( KeyStoreData keyStoreData )
    {
        KeyStore keyStore = null;

        try
        {
            if ( !keyStoreData.getKeyStoreType().isFileBased() )
            {
                LOGGER.error( "NoCreateKeyStoreNotFile.exception.message" );
            }
            else
            {
                File file = new File( keyStoreData.getKeyStoreFile() );

                if ( file.exists() )
                {
                    finStream = new FileInputStream( file );
                    keyStore = KeyStore.getInstance( KeyStore.getDefaultType() );
                    keyStore.load( finStream, keyStoreData.getPassword().toCharArray() );
                }
                else
                {
                    File keyStoresFolder = new File( file.getParent() );
                    if ( keyStoresFolder.mkdirs() )
                    {
                        file.createNewFile();
                    }

                    keyStore = KeyStore.getInstance( keyStoreData.getKeyStoreType().jce() );
                    keyStore.load( null, null );
                    foutStream = new FileOutputStream( file );
                    keyStore.store( foutStream, keyStoreData.getPassword().toCharArray() );
                }
            }
        }
        catch ( java.security.cert.CertificateException e )
        {
            LOGGER.error( "Problem with certificate at keystore.load/store method", e );
        }
        catch ( NoSuchAlgorithmException e )
        {
            LOGGER.error( "Error at keystore.load/store method no such algorithm", e );
        }
        catch ( FileNotFoundException e )
        {
            LOGGER.error( "KeyStore file not found. Please check if it exists at filesystem.", e );
        }
        catch ( KeyStoreException e )
        {
            LOGGER.error( "KeyStore exception while saving/getting keyStore instance", e );
        }
        catch ( IOException e )
        {
            LOGGER.error( "Error accessing keyStore file", e );
        }
        finally
        {
            SafeCloseUtil.close( finStream );
            SafeCloseUtil.close( foutStream );
        }

        return keyStore;
    }


    /**
     * *********************************************************************************** Save changes in the keystore
     *
     * @param keyStore KeyStore
     * @param keyStoreData, KeyStoreData
     */
    public void save( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        try
        {
            if ( !keyStoreData.getKeyStoreType().isFileBased() )
            {
                LOGGER.error( "Keystore is not file-based" );
            }
            else
            {
                File file = new File( keyStoreData.getKeyStoreFile() );
                foutStream = new FileOutputStream( file );
                keyStore.store( foutStream, keyStoreData.getPassword().toCharArray() );
            }
        }
        catch ( IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException ex )
        {
            LOGGER.error( "Error saving keystore", ex );
        }
        finally
        {
            SafeCloseUtil.close( foutStream );
        }
    }


    /**
     * *********************************************************************************** Get Keypair object
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     *
     * @return KeyPair
     */
    public KeyPair getKeyPair( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        KeyPair keyPair = null;

        try
        {
            Key key = keyStore.getKey( keyStoreData.getAlias(), keyStoreData.getPassword().toCharArray() );

            if ( key instanceof PrivateKey )
            {
                Certificate cert = keyStore.getCertificate( keyStoreData.getAlias() );
                PublicKey publicKey = cert.getPublicKey();
                keyPair = new KeyPair( publicKey, ( PrivateKey ) key );
            }
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e )
        {
            LOGGER.error( "Error getting keyStore key or while getting certificate", e );
        }

        return keyPair;
    }


    /**
     * *********************************************************************************** Save x509 Certificate in
     * Keystore
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     * @param x509Cert X509Certificate
     * @param keyPair KeyPair
     */
    public void addNSaveX509Certificate( KeyStore keyStore, KeyStoreData keyStoreData, X509Certificate x509Cert,
                                         KeyPair keyPair )
    {
        try
        {
            keyStore.setKeyEntry( keyStoreData.getAlias(), keyPair.getPrivate(),
                    keyStoreData.getPassword().toCharArray(), new java.security.cert.Certificate[] { x509Cert } );

            save( keyStore, keyStoreData );
        }
        catch ( KeyStoreException e )
        {
            LOGGER.error( "Error setting keyEntry", e );
        }
    }


    /**
     * ***********************************************************************************
     *
     * @param keyStore KeyStore
     *
     * @return String
     */
    public String getEntries( KeyStore keyStore )
    {
        Enumeration<String> enumeration;
        StringBuilder entryData = new StringBuilder( "" );

        try
        {
            enumeration = keyStore.aliases();

            while ( enumeration.hasMoreElements() )
            {
                String alias = ( String ) enumeration.nextElement();
                entryData.append( "\nalias name: " ).append( alias );
                Certificate certificate = keyStore.getCertificate( alias );
                entryData.append( "\nCertificate: " ).append( certificate.toString() );
                entryData.append( "\n\n**************************************" );
            }
        }
        catch ( KeyStoreException e )
        {
            LOGGER.error( "Error retrieving keyStore aliases/getting certificate by alias", e );
        }
        return entryData.toString();
    }


    /**
     * *********************************************************************************** Delete entry in the Keystore
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     *
     * @return boolean
     */
    public boolean deleteEntry( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        try
        {
            keyStore.deleteEntry( keyStoreData.getAlias() );
            //save Keystore file
            this.save( keyStore, keyStoreData );
        }
        catch ( KeyStoreException e )
        {
            LOGGER.error( "Error deleting keyStore entry", e );
        }

        return true;
    }


    /**
     * *********************************************************************************** import Hexadecimal format
     * certificate into Keystore
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     */
    public void importCertificate( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        try
        {
            File file = new File( keyStoreData.getImportFileLocation() );
            finStream = new FileInputStream( file );

            //****************************************************************
            CertificateFactory cf = CertificateFactory.getInstance( "X.509" );
            X509Certificate cert = ( X509Certificate ) cf.generateCertificate( finStream );

            keyStore.setCertificateEntry( keyStoreData.getAlias(), cert );

            //save Keystore file
            this.save( keyStore, keyStoreData );
        }
        catch ( FileNotFoundException e )
        {
            LOGGER.error( "Error accessing file", e );
        }
        catch ( KeyStoreException e )
        {
            LOGGER.error( "Error " );
        }
        catch ( CertificateException e )
        {
            LOGGER.error( "Error getting generating certificate", e );
        }
        finally
        {
            SafeCloseUtil.close( finStream );
        }
    }


    /**
     * *********************************************************************************** Export Certificate as a .cer
     * file.
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     */
    @SuppressWarnings( "restriction" )
    public void exportCertificate( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        try
        {
            X509Certificate cert = ( X509Certificate ) keyStore.getCertificate( keyStoreData.getAlias() );

            File file = new File( keyStoreData.getExportFileLocation() );

            if ( !file.exists() )
            {
                file.mkdirs();
            }

            byte[] buf = cert.getEncoded();

            FileOutputStream os = new FileOutputStream( file );
            os.write( buf );
            os.close();

            Writer wr = null;
            try
            {
                wr = new OutputStreamWriter( os, Charset.forName( "UTF-8" ) );
                wr.write( new sun.misc.BASE64Encoder().encode( buf ) );
                wr.flush();
            }
            finally
            {
                if ( wr != null )
                {
                    wr.close();
                }
            }
        }
        catch ( Exception ex )
        {
            LOGGER.error( "Error KeyStoreManager#exportCertificate", ex );
        }
        finally
        {
            SafeCloseUtil.close( foutStream );
        }
    }


    /**
     * *********************************************************************************** Export Certificate
     *
     * @param keyStore KeyStore ,
     * @param keyStoreData KeyStoreData
     *
     * @return String
     */
    public String exportCertificateInPem( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        try
        {
            X509Certificate cert = ( X509Certificate ) keyStore.getCertificate( keyStoreData.getAlias() );
            return certificateTool.convertX509CertToPem( cert );
        }
        catch ( KeyStoreException e )
        {
            throw new RuntimeException( "Error getting certificate", e );
        }
    }


    /**
     * *********************************************************************************** Import Certificate(HEX) into
     * Keystore
     *
     * @param keyStore KeyStore
     * @param keyStoreData KeyStoreData
     */
    public void importCertificateInPem( KeyStore keyStore, KeyStoreData keyStoreData )
    {
        InputStream inputStream = null;

        try
        {
            X509Certificate cert = certificateTool.convertX509PemToCert( keyStoreData.getHEXCert() );

            keyStore.setCertificateEntry( keyStoreData.getAlias(), cert );

            //save Keystore file
            this.save( keyStore, keyStoreData );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Error importing certificate", e );
        }
        finally
        {
            SafeCloseUtil.close( inputStream );
        }
    }


    /**
     * *********************************************************************************** Check if keystore entry type
     * is a keypair entry
     *
     * @param keyStore KeyStore
     * @param alias String
     *
     * @return boolean
     */
    public static boolean isKeyPairEntry( KeyStore keyStore, String alias ) throws KeyStoreException
    {
        return ( keyStore.isKeyEntry( alias ) ) && ( ( keyStore.getCertificateChain( alias ) != null ) && (
                keyStore.getCertificateChain( alias ).length != 0 ) );
    }


    /**
     * *********************************************************************************** Check if keystore entry type
     * is a key entry
     *
     * @return boolean
     */
    public boolean isKeyEntry( KeyStore keyStoreParam, String alias ) throws KeyStoreException
    {
        return ( keyStoreParam.isKeyEntry( alias ) ) && ( ( keyStoreParam.getCertificateChain( alias ) == null ) || (
                keyStoreParam.getCertificateChain( alias ).length == 0 ) );
    }


    /**
     * *********************************************************************************** Check if keystore entry type
     * is a Trusted Certificate
     *
     * @param alias Sring
     * @param keyStoreParam keyStoreParam
     *
     * @return boolean
     */
    public boolean isTrustedCertificateEntry( KeyStore keyStoreParam, String alias ) throws KeyStoreException
    {
        return ( keyStoreParam.isCertificateEntry( alias ) );
    }


    /**
     * *********************************************************************************** Check if Keystore contains
     * any key data
     *
     * @param keyStore KeyStore
     *
     * @return boolean
     */
    public boolean containsKey( KeyStore keyStore )
    {
        try
        {
            Enumeration<String> aliases = keyStore.aliases();

            while ( aliases.hasMoreElements() )
            {
                String alias = aliases.nextElement();

                if ( isKeyEntry( keyStore, alias ) )
                {
                    return true;
                }
            }

            return false;
        }
        catch ( KeyStoreException ex )
        {
            LOGGER.error( "CheckKeyStoreKeys exception", ex );
            return false;
        }
    }
}
