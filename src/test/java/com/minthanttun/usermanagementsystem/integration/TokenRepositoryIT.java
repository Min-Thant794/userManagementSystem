package com.minthanttun.usermanagementsystem.integration;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.minthanttun.usermanagementsystem.auth.RefreshToken;
import com.minthanttun.usermanagementsystem.common.TokenCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class TokenRepositoryIT extends IntegrationSupport {
    @Autowired
    TokenCleanupJob tokenCleanupJob;

    @Test
    void activeSessionQueryHonorsOwnerRevocationAndStrictExpiryBoundary() {
        var a = user("a");
        var b = user("b");
        var cutoff = OffsetDateTime.parse("2030-01-01T00:00:00Z");
        var good = refresh(a,"good",UUID.randomUUID(),false,cutoff.plusSeconds(1));

        refresh(a,"equal",UUID.randomUUID(),false,cutoff);
        refresh(a,"expired",UUID.randomUUID(),false,cutoff.minusSeconds(1));
        refresh(a,"revoked",UUID.randomUUID(),true,cutoff.plusSeconds(1));
        refresh(b,"other",UUID.randomUUID(),false,cutoff.plusSeconds(1));

        assertThat(refreshTokenRepository.findAllByUser_IdAndRevokedFalseAndExpiresAtAfter(a.getId(),cutoff))
                .extracting(RefreshToken::getId).containsExactly(good.getId());
        assertThat(refreshTokenRepository.findByIdAndUser_Id(good.getId(), b.getId())).isEmpty();
    }

    @Test
    void conditionalRevocationUpdatesExactlyOnce() {
        var t = refresh(user("a"),"t",UUID.randomUUID(),false,future());

        assertThat(tx().<Integer>execute(s -> refreshTokenRepository.revokeIfActive(t.getId()))).isEqualTo(1);
        assertThat(tx().<Integer>execute(s -> refreshTokenRepository.revokeIfActive(t.getId()))).isZero();
        assertThat(refreshTokenRepository.findById(t.getId()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void familyRevocationLeavesOtherFamiliesAlone() {
        var a = user("a"); var family = UUID.randomUUID();
        var one = refresh(a,"one",family,false,future());
        var two = refresh(a,"two",family,false,future());
        var other = refresh(a,"other",UUID.randomUUID(),false,future());

        assertThat(tx().<Integer>execute(s -> refreshTokenRepository.revokeFamily(family))).isEqualTo(2);
        assertThat(refreshTokenRepository.findById(one.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(two.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(other.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeAllExceptPreservesCurrentSessionAndOtherUser() {
        var a = user("a"); var b = user("b");
        var current = refresh(a,"current",UUID.randomUUID(),false,future());
        var old = refresh(a,"old",UUID.randomUUID(),false,future());
        var other = refresh(b,"other",UUID.randomUUID(),false,future());

        assertThat(tx().<Integer>execute(s -> refreshTokenRepository.revokeAllExcept(a.getId(), current.getId()))).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(current.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(refreshTokenRepository.findById(old.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(other.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(tx().<Integer>execute(s -> refreshTokenRepository.revokedAllForUser(a.getId()))).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(other.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void allThreeCleanupQueriesUseOrAndStrictBefore() {
        var u=user("cleanup"); var cutoff=OffsetDateTime.parse("2030-01-01T00:00:00Z");
        // Each table: expired only, consumed only, both, equal-to-cutoff, future unused.
        for (int i=0;i<5;i++) {
            boolean consumed=i==1||i==2;
            var expiry=(i==0||i==2)?cutoff.minusSeconds(1):i==3?cutoff:cutoff.plusSeconds(1);
            refresh(u,"r"+i,UUID.randomUUID(),consumed,expiry);
            reset(u,"p"+i,consumed,expiry); verification(u,"v"+i,consumed,expiry);
        }

        tx().executeWithoutResult(s -> {
            assertThat(refreshTokenRepository.deleteByRevokedTrueOrExpiresAtBefore(cutoff)).isEqualTo(3);
            assertThat(passwordResetTokenRepository.deleteByUsedTrueOrExpiresAtBefore(cutoff)).isEqualTo(3);
            assertThat(emailVerificationTokenRepository.deleteByUsedTrueOrExpiresAtBefore(cutoff)).isEqualTo(3);
        });

        assertThat(refreshTokenRepository.findAll()).extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder(tokenHasher.hash("r3"),tokenHasher.hash("r4"));
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void scheduledJobMethodCommitsCleanupAndCanRunTwice() {
        var u=user("job"); var past=OffsetDateTime.now().minusDays(1);
        refresh(u,"r",UUID.randomUUID(),false,past); reset(u,"p",true,future());
        verification(u,"v",false,past);

        tokenCleanupJob.cleanUpExpiredTokens();
        tokenCleanupJob.cleanUpExpiredTokens();

        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(passwordResetTokenRepository.count()).isZero();
        assertThat(emailVerificationTokenRepository.count()).isZero();
    }
}