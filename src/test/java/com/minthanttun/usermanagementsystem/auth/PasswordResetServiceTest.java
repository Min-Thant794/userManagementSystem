package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.ForgotPasswordRequest;
import com.minthanttun.usermanagementsystem.auth.dto.ResetPasswordRequest;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.security.jwt.SessionRevocationService;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class PasswordResetServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    PasswordEncoder encoder;

    @Mock
    TokenHasher tokenHasher;

    @Mock
    EmailService emailService;

    @Mock
    SessionRevocationService sessionRevocationService;

    @InjectMocks
    PasswordResetService passwordResetService;

    private User user;

    @BeforeEach void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("old-hash")
                .build();
    }

    private PasswordResetToken validToken() {
        return PasswordResetToken.builder().user(user).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusHours(1)).used(false).build();
    }

    @Test void forgotPasswordReplacesUnusedTokensAndSendsRawToken() {
        PasswordResetToken passwordResetToken = validToken();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        when(passwordResetTokenRepository.findAllByUser_IdAndUsedFalse(user.getId())).thenReturn(List.of(passwordResetToken));
        when(tokenHasher.hash(anyString())).thenReturn("stored-hash");
        OffsetDateTime before = OffsetDateTime.now();

        passwordResetService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);

        verify(passwordResetTokenRepository).save(saved.capture());
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), raw.capture());
        verify(tokenHasher).hash(raw.getValue());
        assertThat(raw.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(passwordResetToken.isUsed()).isTrue();
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getTokenHash()).isEqualTo("stored-hash").isNotEqualTo(raw.getValue());
        assertThat(saved.getValue().isUsed()).isFalse();
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(before.plusMinutes(30), OffsetDateTime.now().plusMinutes(30));
    }

    @Test void validResetSavesEncodedPasswordConsumesTokenAndRevokesSessions() {
        PasswordResetToken token = validToken();
        when(tokenHasher.hash("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(encoder.encode("NewPassword123!")).thenReturn("new-hash");

        passwordResetService.resetPassword(new ResetPasswordRequest("raw", "NewPassword123!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(sessionRevocationService).revokeAllSessions(user.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "used", "expired", "missing-user"})
    void rejectsInvalidResetWithoutPasswordOrSessionChanges(String scenario) {
        PasswordResetToken token = validToken();
        if (scenario.equals("used")) token.setUsed(true);
        if (scenario.equals("expired")) token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(tokenHasher.hash("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHash("hash")).thenReturn(scenario.equals("unknown") ? Optional.empty() : Optional.of(token));
        if (scenario.equals("missing-user")) when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(new ResetPasswordRequest("raw", "NewPassword123!")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(encoder, sessionRevocationService);
    }
}
