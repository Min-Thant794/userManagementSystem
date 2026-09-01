package com.minthanttun.usermanagementsystem.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteAllByUser_Id(UUID userId);
    long deleteByRevokedTrueOrExpiresAtBefore(OffsetDateTime cutoff);
}
