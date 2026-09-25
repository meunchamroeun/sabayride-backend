package com.sabayride.identity.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the RSA public key as a JWKS so other services (and the gateway)
 * verify access tokens without ever holding the private key.
 * Reachable internally at http://identity-service:8081/.well-known/jwks.json.
 */
@RestController
public class JwksController {

    private final JwtService jwt;

    public JwksController(JwtService jwt) {
        this.jwt = jwt;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        RSAPublicKey pub = jwt.publicKey();
        RSAKey jwk = new RSAKey.Builder(pub)
                .keyID(jwt.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keys", List.of(jwk.toPublicJWK().toJSONObject()));
        return body;
    }
}
