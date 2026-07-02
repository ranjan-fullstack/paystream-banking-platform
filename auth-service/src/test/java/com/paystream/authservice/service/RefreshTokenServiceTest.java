package com.paystream.authservice.service;

import com.paystream.authservice.entity.RefreshToken;
import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import com.paystream.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService unit tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("ananya.gupta")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Should create and persist a new refresh token for a user")
    void testCreateRefreshTokenSuccess() {
        // Given
        User user = buildUser();
        when(refreshTokenRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        String token = refreshTokenService.createRefreshToken(user);

        // Then
        assertThat(token).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepo).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getExpiryDate()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should return the associated user for a valid, unexpired refresh token")
    void testValidateRefreshTokenSuccess() {
        // Given
        User user = buildUser();
        RefreshToken token = RefreshToken.builder()
                .id(10L)
                .token("valid-token-abc")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(user)
                .build();
        when(refreshTokenRepo.findByToken("valid-token-abc")).thenReturn(Optional.of(token));

        // When
        User result = refreshTokenService.validateRefreshToken("valid-token-abc");

        // Then
        assertThat(result).isEqualTo(user);
    }

    @Test
    @DisplayName("Should throw exception when the refresh token does not exist")
    void testValidateRefreshTokenNotFound_throwsException() {
        // Given
        when(refreshTokenRepo.findByToken("unknown-token")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("unknown-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    @DisplayName("Should throw exception when the refresh token has expired")
    void testRefreshTokenExpired_throwsException() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .id(11L)
                .token("expired-token-xyz")
                .expiryDate(Instant.now().minusSeconds(10))
                .user(buildUser())
                .build();
        when(refreshTokenRepo.findByToken("expired-token-xyz")).thenReturn(Optional.of(expiredToken));

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token-xyz"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token expired");
    }
}
