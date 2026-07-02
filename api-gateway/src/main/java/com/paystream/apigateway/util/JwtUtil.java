package com.paystream.apigateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPublicKey;

@Component
public class JwtUtil {

    @Value("${jwt.keystore.path:classpath:paystream-jwt.jks}")
    private Resource keystoreResource;

    @Value("${jwt.keystore.store-password:paystream-keystore-password}")
    private String storePassword;

    @Value("${jwt.keystore.key-alias:jwt-signing-key}")
    private String keyAlias;

    private RSAPublicKey rsaPublicKey;

    @PostConstruct
    void loadPublicKey() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream in = keystoreResource.getInputStream()) {
                ks.load(in, storePassword.toCharArray());
            }
            rsaPublicKey = (RSAPublicKey) ks.getCertificate(keyAlias).getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT RSA public key from keystore", e);
        }
    }

    public void validateToken(String token) {
        Jwts.parserBuilder()
                .setSigningKey(rsaPublicKey)
                .build()
                .parseClaimsJws(token);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(rsaPublicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
