package com.minthanttun.usermanagementsystem.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteAllByUser_Id(UUID userId);
    long deleteByRevokedTrueOrExpiresAtBefore(OffsetDateTime cutoff);

    List<RefreshToken> findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(UUID userId, OffsetDateTime now);
    Optional<RefreshToken> findByIdAndUser_Id(Long id, UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.id = :id AND t.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.familyId = :familyId AND t.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    int revokedAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.id != :excludeId AND t.revoked = false")
    int revokeAllExcept(@Param("userId") UUID userId, @Param("excludeId") Long excludeId);
}