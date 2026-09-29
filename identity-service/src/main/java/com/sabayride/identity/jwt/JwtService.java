package com.sabayride.identity.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * The only place that mints access tokens. Loads the RSA private key
 * (PKCS#8 PEM) from configuration, signs short-lived RS256 JWTs, and exposes the
 * matching public key for the JWKS document other services verify against.
 *
 * The public key is DERIVED from the private key, so only the private PEM needs
 * to be present at runtime.
 */
@Component
public class JwtService {

    private final Path privateKeyPath;
    private final String issuer;
    private final String keyId;
    private final int accessMinutes;

    private RSAPrivateCrtKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(
            @Value("${sabayride.jwt.private-key-path}") String privateKeyPath,
            @Value("${sabayride.jwt.issuer:sabayride-identity}") String issuer,
            @Value("${sabayride.jwt.key-id:sabayride-2026}") String keyId,
            @Value("${sabayride.jwt.access-token-minutes:15}") int accessMinutes) {
        this.privateKeyPath = Path.of(privateKeyPath);
        this.issuer = issuer;
        this.keyId = keyId;
        this.accessMinutes = accessMinutes;
    }

    @PostConstruct
    void load() throws Exception {
        byte[] der = Base64.getMimeDecoder().decode(
                Files.readString(privateKeyPath)
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", ""));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        privateKey = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        publicKey = (RSAPublicKey) kf.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
    }

    /** Sign an RS256 access token for the given subject + roles. */
    public String sign(UUID subject, Collection<String> roles) {
        try {
            Date now = new Date();
            Date exp = new Date(now.getTime() + (long) accessMinutes * 60_000L);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject.toString())
                    .claim("roles", roles)
                    .issueTime(now)
                    .expirationTime(exp)
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign access token", e);
        }
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    public long accessTokenSeconds() {
        return (long) accessMinutes * 60L;
    }

    public UUID parseUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (signedJWT.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey))) {
                Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
                if (exp != null && exp.after(new Date())) {
                    return UUID.fromString(signedJWT.getJWTClaimsSet().getSubject());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Sign a short-lived (5 min) token scoped exclusively to PASSWORD_RESET. */
    public String signPasswordResetToken(UUID subject) {
        try {
            Date now = new Date();
            Date exp = new Date(now.getTime() + 5 * 60_000L); // 5 minutes per spec
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject.toString())
                    .claim("scope", "PASSWORD_RESET")
                    .issueTime(now)
                    .expirationTime(exp)
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign password reset token", e);
        }
    }

    /** Verify and extract subject from a PASSWORD_RESET token. */
    public UUID parsePasswordResetToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token.trim());
            if (signedJWT.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey))) {
                Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
                String scope = (String) signedJWT.getJWTClaimsSet().getClaim("scope");
                if ("PASSWORD_RESET".equals(scope) && exp != null && exp.after(new Date())) {
                    return UUID.fromString(signedJWT.getJWTClaimsSet().getSubject());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

