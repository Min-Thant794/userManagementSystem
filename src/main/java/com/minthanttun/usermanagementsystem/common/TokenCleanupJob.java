package com.minthanttun.usermanagementsystem.common;

import com.minthanttun.usermanagementsystem.auth.EmailVerificationTokenRepository;
import com.minthanttun.usermanagementsystem.auth.PasswordResetTokenRepository;
import com.minthanttun.usermanagementsystem.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Scheduled(cron = "0 0 3 * * *") //everyday at 3AM server time
    @Transactional
    public void cleanUpExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();

        long refreshDeleted = refreshTokenRepository.deleteByRevokedTrueOrExpiresAtBefore(now);
        long resetDeleted = passwordResetTokenRepository.deleteByUsedTrueOrExpiresAtBefore(now);
        long verificationDeleted = emailVerificationTokenRepository.deleteByUsedTrueOrExpiresAtBefore(now);

        log.info("Token cleanup: removed {} refresh tokens, {} password reset tokens, {} email verification tokens",
                refreshDeleted, resetDeleted, verificationDeleted);
    }
}
