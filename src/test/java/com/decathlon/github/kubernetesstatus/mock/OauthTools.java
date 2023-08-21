package com.decathlon.github.kubernetesstatus.mock;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;

import java.util.Date;
import java.util.UUID;

public class OauthTools {
    public static final RSAKey jwk = OauthTools.generateKey();

    public static final String CLIENT_CREDENTIAL = OauthTools.createToken("idp-test.localhost", null, jwk);

    public static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
                    .keyID(UUID.randomUUID().toString()) // give the key a unique ID
                    .generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String createToken(String issuer, String uid, RSAKey jwk) {
        try {
            var builder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .expirationTime(new Date(new Date().getTime() + 1000 * 60 * 60))
                    .notBeforeTime(new Date())
                    .issueTime(new Date())
                    .jwtID(UUID.randomUUID().toString());

            if (uid != null) {
                builder = builder.subject(uid);
            }

            JWTClaimsSet jwtClaims = builder.build();

            JWSObject jwsObject = new JWSObject(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwk.getKeyID()).build(),
                    new Payload(jwtClaims.toJSONObject()));

            jwsObject.sign(new RSASSASigner(jwk));

            return jwsObject.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

