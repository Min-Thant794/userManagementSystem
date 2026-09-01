package com.minthanttun.usermanagementsystem.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    long deleteByUsedTrueOrExpiresAtBefore(OffsetDateTime cutoff);
    List<PasswordResetToken> findAllByUser_IdAndUsedFalse(UUID userId);
}