package com.personalfinance.user.service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.personalfinance.user.entity.UserEntity;

/**
 * Issues and validates RS256 access tokens. The public key is published at
 * /api/v1/auth/jwks so the gateway (and later, other services) can validate
 * tokens without sharing secrets.
 *
 * Key material comes from AUTH_JWT_PRIVATE_KEY_PEM (PKCS#8). When absent, an
 * ephemeral key pair is generated: fine for dev, but every restart invalidates
 * outstanding access tokens.
 */
@Service
public class JwtService {

    public static final String ISSUER = "personal-finance/user-service";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${auth.jwt.private-key-pem:}") String privateKeyPem,
            @Value("${auth.access-token-ttl:15m}") Duration accessTokenTtl) throws Exception {
        this.accessTokenTtl = accessTokenTtl;
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            RSAPrivateCrtKey key = parsePkcs8(privateKeyPem);
            this.privateKey = key;
            this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
        } else {
            log.warn("No auth.jwt.private-key-pem configured — generating an ephemeral RSA key pair. "
                    + "Access tokens will not survive a restart.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey = (RSAPublicKey) pair.getPublic();
        }
        this.keyId = UUID.nameUUIDFromBytes(publicKey.getEncoded()).toString();
    }

    public String issueAccessToken(UserEntity user) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .issuer(ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(accessTokenTtl)))
                    .claim("email", user.getEmail())
                    .claim("name", user.getDisplayName())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    public Optional<JWTClaimsSet> validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> jwks() {
        RSAKey jwk = new RSAKey.Builder(publicKey).keyID(keyId).build();
        return new JWKSet(jwk).toJSONObject();
    }

    private static RSAPrivateCrtKey parsePkcs8(String pem) throws Exception {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
