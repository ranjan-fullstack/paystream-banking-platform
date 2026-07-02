package com.paystream.authservice.security;

import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import com.paystream.authservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService unit tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Should load user details with the user's role as a granted authority")
    void testLoadUserByUsername_Success() {
        // Given
        User user = User.builder()
                .id(1L)
                .username("kiran.patel")
                .password("encoded-password")
                .role(Role.TELLER)
                .build();
        when(userRepo.findByUsername("kiran.patel")).thenReturn(Optional.of(user));

        // When
        UserDetails details = customUserDetailsService.loadUserByUsername("kiran.patel");

        // Then
        assertThat(details.getUsername()).isEqualTo("kiran.patel");
        assertThat(details.getPassword()).isEqualTo("encoded-password");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("TELLER");
    }

    @Test
    @DisplayName("Should throw exception when the username does not exist")
    void testLoadUserByUsername_NotFound_throwsException() {
        // Given
        when(userRepo.findByUsername("ghost.user")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("ghost.user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost.user");
    }
}
