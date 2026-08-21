package com.paystream.authservice.service;

import com.paystream.authservice.dto.AuthResponse;
import com.paystream.authservice.dto.LoginRequest;
import com.paystream.authservice.dto.RefreshTokenRequest;
import com.paystream.authservice.dto.RegisterRequest;
import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import com.paystream.authservice.repository.UserRepository;
import com.paystream.authservice.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should register a new customer successfully with encoded password")
    void testRegisterSuccess() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ravi.kumar");
        request.setPassword("SecurePass@123");

        when(passwordEncoder.encode("SecurePass@123")).thenReturn("encoded-password");

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("ravi.kumar");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("Should throw exception when registering a duplicate username")
    void testRegisterDuplicateUsername_throwsException() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("priya.sharma");
        request.setPassword("Password@123");

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepo.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for username"));

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should login successfully and return access and refresh tokens")
    void testLoginSuccess() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("amit.verma");
        request.setPassword("Password@123");

        User user = User.builder()
                .id(1L)
                .username("amit.verma")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .build();

        when(userRepo.findByUsername("amit.verma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token-xyz");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token-abc");

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-xyz");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-abc");
    }

    @Test
    @DisplayName("Should throw exception when login password is invalid")
    void testLoginInvalidPassword_throwsException() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("amit.verma");
        request.setPassword("WrongPassword");

        User user = User.builder()
                .id(1L)
                .username("amit.verma")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .build();

        when(userRepo.findByUsername("amit.verma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "encoded-password")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Should throw exception when login user is not found")
    void testLoginUserNotFound_throwsException() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown.user");
        request.setPassword("Password@123");

        when(userRepo.findByUsername("unknown.user")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Should refresh access token successfully when refresh token is valid")
    void testRefreshTokenSuccess() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        User user = User.builder()
                .id(2L)
                .username("sunita.rao")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .build();

        when(refreshTokenService.validateRefreshToken("valid-refresh-token")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("new-access-token");

        // When
        AuthResponse response = authService.refreshToken(request);

        // Then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
    }

    @Test
    @DisplayName("Should throw exception when refresh token is expired")
    void testRefreshTokenExpired_throwsException() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-refresh-token");

        when(refreshTokenService.validateRefreshToken("expired-refresh-token"))
                .thenThrow(new RuntimeException("Refresh token expired"));

        // When & Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token expired");

        verify(jwtService, never()).generateToken(any());
    }
}
