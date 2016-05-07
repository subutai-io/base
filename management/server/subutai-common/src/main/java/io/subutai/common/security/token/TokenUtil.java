package io.subutai.common.security.token;


import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import net.minidev.json.JSONObject;

/**
 * Token management Utility with JOSE library
 */
public class TokenUtil
{

    //************************************************
    public static String createToken(String headerJson, String claimJson,String sharedKey)
    {
        try
        {
            JWSHeader header = JWSHeader.parse(headerJson) ;
            JWSSigner signer = new MACSigner( sharedKey.getBytes() );
            JWTClaimsSet claimsSet = JWTClaimsSet.parse( claimJson ) ;

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign( signer );

            return signedJWT.serialize();
        }
        catch ( Exception ex )
        {
            return "";
        }
    }


    //************************************************
    public static boolean verifySignature(String token, String sharedKey)
    {
        boolean verifiedSignature = false;

        try
        {
            JWSObject jwsObject  = JWSObject.parse( token );
            JWSVerifier verifier = new MACVerifier( sharedKey.getBytes() );
            verifiedSignature = jwsObject.verify( verifier );
        }
        catch ( Exception ignore )
        {
        }

        return verifiedSignature;
    }


    //************************************************
    public static Payload parseToken(String token)
    {
        Payload payload = null;
        try
        {
            JWSObject jwsObject = JWSObject.parse( token );
            payload = jwsObject.getPayload();
        }
        catch ( Exception ex )
        {
            return null;
        }

        return payload;
    }


    //************************************************
    public static String getSubject(String token)
    {
        try
        {
            Payload payload   = parseToken(token);
            JSONObject obj = payload.toJSONObject();
            return obj.get( "sub" ).toString();
        }
        catch ( Exception ex )
        {
            return null;
        }
    }


    //************************************************
    public static boolean verifyToken(String token, String sharedKey)
    {
        return verifySignatureAndDate(token, sharedKey);
    }


    //************************************************
    public static boolean verifySignatureAndDate(String token, String sharedKey)
    {
        try
        {
            JWSObject jwsObject  = JWSObject.parse( token );
            JWSVerifier verifier = new MACVerifier( sharedKey.getBytes() );

            if(jwsObject.verify( verifier ))
            {
                long date = getDate(token,jwsObject);
                return System.currentTimeMillis() <= date;
            }
            else
            {
                return false;
            }
        }
        catch ( Exception ex )
        {
            return false;
        }
    }


    //************************************************
    public static boolean isDateValid(String token)
    {
        long date = getDate(token);

        return System.currentTimeMillis() <= date;
    }



    //************************************************
    public static Payload parseToken(String token, JWSObject jwsObject)
    {
        Payload payload = null;
        try
        {
            payload = jwsObject.getPayload();
        }
        catch ( Exception ignore )
        {
        }

        return payload;
    }


    //************************************************
    public static long getDate(String token)
    {
        try
        {
            Payload payload   = parseToken(token);
            JSONObject obj = payload.toJSONObject();
            return (long)obj.get( "exp" );
        }
        catch ( Exception ex )
        {
            return 0;
        }
    }


    //************************************************
    public static long getDate(String token, JWSObject jwsObject)
    {
        try
        {
            Payload payload   = parseToken(token, jwsObject);
            JSONObject obj = payload.toJSONObject();
            return (long)obj.get( "exp" );
        }
        catch ( Exception ex )
        {
            return 0;
        }
    }


    //************************************************
    public static String createTokenRSA(PrivateKey privateKey,String headerJson, String claimJson)
    {
        try
        {
            JWSSigner signer = new RSASSASigner((RSAPrivateKey )privateKey);

            Payload pl = new Payload(claimJson);
            JWSObject jwsObject = new JWSObject(new JWSHeader( JWSAlgorithm.RS256),pl);

            jwsObject.sign(signer);

            return jwsObject.serialize();
        }
        catch(Exception ex)
        {
            return "";
        }
    }


    //************************************************
    public static boolean verifyTokenRSA(PublicKey pKey, String token)
    {
        try
        {
            Payload pl = new Payload(token);
            JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.RS256),pl);
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey )pKey);

            return jwsObject.verify(verifier);
        }
        catch ( JOSEException e )
        {
            return false;
        }
    }


    //************************************************
    private static KeyPair generateRSAKeyPair()
    {
        try
        {
            KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance( "RSA" );
            keyGenerator.initialize(1024);
            return keyGenerator.genKeyPair();
        }
        catch(Exception ex)
        {
            return null;
        }
    }



}
