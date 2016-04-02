package io.subutai.core.hubmanager.impl;


import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;


public class FingerprintTrustManager implements X509TrustManager
{
    private byte[] serverFingerprint;

    public FingerprintTrustManager( final byte[] serverFingerprint )
    {
        this.serverFingerprint = serverFingerprint;
    }


    @Override
    public void checkClientTrusted( final X509Certificate[] x509Certificates, final String s )
            throws CertificateException
    {

    }


    @Override
    public void checkServerTrusted( final X509Certificate[] chain, final String authType ) throws CertificateException
    {
        X509Certificate cert = chain[0];

        try
        {
            cert.checkValidity();
        }
        catch ( Exception e )
        {
            throw new CertificateException( e.toString() );
        }
    }


    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        return new X509Certificate[0];
    }
}