package com.paystream.authservice.security;

import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService unit tests")
class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        // test-jwt-keystore.jks is a throwaway keystore generated solely for this
        // test -- see auth-service/src/test/resources/README.md. Never the real
        // production keystore (that's loaded from Secrets Manager, not committed
        // anywhere), and deliberately named/passworded differently so the two
        // can't be confused.
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "keystoreResource", new ClassPathResource("test-jwt-keystore.jks"));
        ReflectionTestUtils.setField(jwtService, "storePassword", "test-only-password");
        ReflectionTestUtils.setField(jwtService, "keyAlias", "jwt-signing-key");
        ReflectionTestUtils.setField(jwtService, "keyPassword", "test-only-password");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L);
        jwtService.loadKeyPair();

        user = User.builder()
                .id(1L)
                .username("deepak.mehta")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Should generate a non-blank RS256 JWT token containing the username subject")
    void testGenerateToken() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.extractUsername(token)).isEqualTo("deepak.mehta");
    }

    @Test
    @DisplayName("Should embed a JTI (unique JWT ID) in every generated token")
    void testGenerateToken_containsJti() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractJti(token)).isNotBlank();
    }

    @Test
    @DisplayName("Should validate a freshly generated token as valid for the matching username")
    void testValidateTokenSuccess() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, "deepak.mehta")).isTrue();
    }

    @Test
    @DisplayName("Should throw ExpiredJwtException when validating an expired token")
    void testValidateTokenExpired() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -10000L);
        String expiredToken = jwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, "deepak.mehta"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Should extract username from a valid token")
    void testExtractUsername() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("deepak.mehta");
    }
}
