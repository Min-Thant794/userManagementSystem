package com.minthanttun.usermanagementsystem.auth;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.auth.dto.*;
import com.minthanttun.usermanagementsystem.common.exception.*;
import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.user.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("remaining-unit")
public class AuthServiceAdditionalTest {
    @Mock LoginAttemptService loginAttemptService;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock TokenHasher tokenHasher;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock TokenIssuer tokenIssuer;
    @Mock EmailVerificationService emailVerificationService;
    @InjectMocks AuthService authService;

    @Test
    void signupUsersSafeDefaultsReturnsSavedEntityAndRequestsVerification() {
        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "+6591111111", "Password123!");
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        User persisted = User.builder().id(UUID.randomUUID()).username("testuser").email("test@example.com").phoneNumber("+6591111111").passwordHash("encoded-password").build();
        when(userRepository.save(any(User.class))).thenReturn(persisted);
        User result = authService.signup(signupRequest);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(result).isSameAs(persisted);
        verify(emailVerificationService).generateVerificationEmail(persisted, "test@example.com");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void disabledAccountMapsToSuspendedExceptionWithoutFailedAttemptOrTokens() {
        User user = User.builder().username("testuser").emailVerified(true).build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("testuser", "Password123!"), mock(HttpServletRequest.class)))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessage("This account has been suspended or is temporarily locked");
        verifyNoInteractions(loginAttemptService, tokenIssuer);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownIdentifierIsPassedToAuthenticationAndFailsWithoutTokens() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "bad-password"), mock(HttpServletRequest.class)))
                .isInstanceOf(InvalidCredentialsException.class);
        ArgumentCaptor<UsernamePasswordAuthenticationToken> credentials = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(credentials.capture());
        assertThat(credentials.getValue().getPrincipal()).isEqualTo("unknown");
        assertThat(credentials.getValue().getCredentials()).isEqualTo("bad-password");
        verify(loginAttemptService).recordFailedAttempt("unknown");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void failedEmailLoginRecordsAttemptAgainstResolvedUsername() {
        User user = User.builder().username("testuser").email("test@example.com").emailVerified(true).build();
        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("wrong password"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong"), mock(HttpServletRequest.class)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).recordFailedAttempt("testuser");
        verify(loginAttemptService, never()).recordFailedAttempt("test@example.com");
        ArgumentCaptor<UsernamePasswordAuthenticationToken> credentials = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(credentials.capture());
        assertThat(credentials.getValue().getPrincipal()).isEqualTo("testuser");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void repeatedLogoutOfRevokedTokenIsHarmless() {
        RefreshToken refreshToken = RefreshToken.builder().id(1L).tokenHash("hash").revoked(true).build();
        when(tokenHasher.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(refreshToken));
        authService.logout("raw");
        authService.logout("raw");
        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(refreshToken);
        verifyNoInteractions(tokenIssuer);
    }
}
