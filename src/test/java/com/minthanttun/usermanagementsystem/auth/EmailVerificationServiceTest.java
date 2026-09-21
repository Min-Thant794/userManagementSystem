package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.common.exception.*;
import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class EmailVerificationServiceTest {
    @Mock UserRepository userRepository;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock TokenHasher tokenHasher;
    @Mock EmailService emailService;
    @Mock CacheManager cacheManager;
    @Mock Cache userCache;
    @Mock Cache adminCache;
    @Mock SessionRevocationService sessionRevocationService;
    @InjectMocks EmailVerificationService emailVerificationService;
    private User user;

    @BeforeEach void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("old@example.com")
                .emailVerified(false)
                .build();
    }

    private EmailVerificationToken emailVerificationToken() {
        return EmailVerificationToken.builder()
                .user(user)
                .tokenHash("hash")
                .used(false)
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();
    }

    @Test void generationInvalidatesPreviousTokensAndEmailRawTokenToTarget() {
        EmailVerificationToken old = emailVerificationToken();

        when(emailVerificationTokenRepository.findAllByUser_IdAndUsedFalse(user.getId()))
                .thenReturn(List.of(old));

        when(tokenHasher.hash(anyString())).thenReturn("stored-hash");
        OffsetDateTime before = OffsetDateTime.now();

        emailVerificationService.generateVerificationEmail(user, "new@example.com");

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);

        verify(emailVerificationTokenRepository).save(saved.capture());
        verify(emailService).sendVerificationEmail(eq("new@example.com"), raw.capture());
        verify(tokenHasher).hash(raw.getValue());
        assertThat(raw.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(old.isUsed()).isTrue();
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getTokenHash()).isEqualTo("stored-hash").isNotEqualTo(raw.getValue());
        assertThat(saved.getValue().isUsed()).isFalse();

        assertThat(saved.getValue().getExpiresAt()).isBetween(before.plusHours(24), OffsetDateTime.now().plusHours(24));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void verifiesEmailConsumesTokenAndEvictBothCaches(boolean changingEmail) {
        if (changingEmail) {
            user.setPendingEmail("new@example.com");
        }
        EmailVerificationToken token = emailVerificationToken();
        when(tokenHasher.hash("raw")).thenReturn("hash");

        when(emailVerificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(cacheManager.getCache("users")).thenReturn(userCache);
        when(cacheManager.getCache("adminUsers")).thenReturn(adminCache);

        emailVerificationService.verifyEmail("raw");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmail()).isEqualTo(changingEmail ? "new@example.com" : "old@example.com");
        assertThat(user.getPendingEmail()).isNull();
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
        verify(userCache).evict(user.getId());
        verify(adminCache).evict(user.getId());
        if (changingEmail) {
            verify(userRepository).existsByEmail("new@example.com");
            verify(sessionRevocationService).revokeAllSessions(user.getId());
        } else {
            verifyNoInteractions(sessionRevocationService);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "used", "expired", "missing-user", "email-taken"})
    void rejectInvalidVerificationWithoutSavingOrEvicting(String scenario) {
        EmailVerificationToken token = emailVerificationToken();
        if (scenario.equals("used")) {
            token.setUsed(true);
        }

        if (scenario.equals("expired")) {
            token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        }

        when(tokenHasher.hash("raw")).thenReturn("hash");

        when(emailVerificationTokenRepository.findByTokenHash("hash")).thenReturn(scenario.equals("unknown") ?
                Optional.empty(): Optional.of(token));

        if (scenario.equals("missing-user")) {
            when(userRepository.findById(user.getId())).thenReturn(Optional.empty());
        }

        if (scenario.equals("email-taken")) {
            user.setPendingEmail("taken@example.com");

            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        }

        Class<? extends RuntimeException> expected = scenario.equals("email-taken") ? DuplicateResourceException.class : InvalidCredentialsException.class;

        assertThatThrownBy(() -> emailVerificationService.verifyEmail("raw")).isInstanceOf(expected);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).save(any());
        verify(emailVerificationTokenRepository, never()).save(any());
        verifyNoInteractions(cacheManager, sessionRevocationService);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resendsToPendingEmailOrCurrentEmailForUnverifiedUser(boolean hasPendingEmail) {
        if (hasPendingEmail) user.setPendingEmail("pending@example.com");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenHasher.hash(anyString())).thenReturn("hash");
        emailVerificationService.resendVerification(user.getEmail());
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(eq(hasPendingEmail ? "pending@example.com" : user.getEmail()), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resendDoesNothingForUnknownOrAlreadyVerifiedUser(boolean exists) {
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(exists ? Optional.of(user) : Optional.empty());
        emailVerificationService.resendVerification(user.getEmail());
        verifyNoInteractions(emailVerificationTokenRepository, tokenHasher, emailService, sessionRevocationService, cacheManager);
    }
}
