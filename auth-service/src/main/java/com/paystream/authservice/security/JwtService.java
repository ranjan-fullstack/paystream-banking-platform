package com.paystream.authservice.security;

import com.paystream.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${spring.security.jwt.keystore.path:classpath:paystream-jwt.jks}")
    private Resource keystoreResource;

    @Value("${spring.security.jwt.keystore.store-password:paystream-keystore-password}")
    private String storePassword;

    @Value("${spring.security.jwt.keystore.key-alias:jwt-signing-key}")
    private String keyAlias;

    @Value("${spring.security.jwt.keystore.key-password:paystream-keystore-password}")
    private String keyPassword;

    @Value("${spring.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;

    @PostConstruct
    void loadKeyPair() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream in = keystoreResource.getInputStream()) {
                ks.load(in, storePassword.toCharArray());
            }
            rsaPrivateKey = (RSAPrivateKey) ks.getKey(keyAlias, keyPassword.toCharArray());
            rsaPublicKey = (RSAPublicKey) ks.getCertificate(keyAlias).getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT RSA keypair from keystore", e);
        }
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("userId", user.getId())
                .claim("isFirstLogin", user.isFirstLogin())
                .claim("branchCode", user.getBranchCode())
                .claim("employeeId", user.getEmployeeId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(rsaPrivateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public boolean isTokenValid(String token, String username) {
        return extractUsername(token).equals(username) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public RSAPublicKey getPublicKey() {
        return rsaPublicKey;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(rsaPublicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
