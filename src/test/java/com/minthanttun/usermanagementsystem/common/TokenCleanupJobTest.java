package com.minthanttun.usermanagementsystem.common;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.auth.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("remaining-unit")
public class TokenCleanupJobTest {
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @InjectMocks TokenCleanupJob tokenCleanupJob;

    @ParameterizedTest
    @ValueSource(longs = {0, 5})
    void deletesEachTokenCategoryWithOneSharedCutoff(long deleted) {
        when (refreshTokenRepository.deleteByRevokedTrueOrExpiresAtBefore(any())).thenReturn(deleted);
        when(passwordResetTokenRepository.deleteByUsedTrueOrExpiresAtBefore(any())).thenReturn(deleted);
        when(emailVerificationTokenRepository.deleteByUsedTrueOrExpiresAtBefore(any())).thenReturn(deleted);
        OffsetDateTime before = OffsetDateTime.now();
        tokenCleanupJob.cleanUpExpiredTokens();
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(refreshTokenRepository).deleteByRevokedTrueOrExpiresAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, OffsetDateTime.now());
        verify(passwordResetTokenRepository).deleteByUsedTrueOrExpiresAtBefore(cutoff.getValue());
        verify(emailVerificationTokenRepository).deleteByUsedTrueOrExpiresAtBefore(cutoff.getValue());
        verifyNoMoreInteractions(refreshTokenRepository, passwordResetTokenRepository, emailVerificationTokenRepository);
    }
}
