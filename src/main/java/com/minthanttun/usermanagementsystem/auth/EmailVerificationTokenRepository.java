package com.minthanttun.usermanagementsystem.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    long deleteByUsedTrueOrExpiresAtBefore(OffsetDateTime cutoff);
    List<EmailVerificationToken> findAllByUser_IdAndUsedFalse(UUID userId);
}
