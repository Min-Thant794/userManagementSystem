package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.auth.AuthService;
import com.minthanttun.usermanagementsystem.common.exception.*;
import com.minthanttun.usermanagementsystem.security.jwt.TokenIssuer;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTransactionIT extends IntegrationSupport {
    @Autowired
    AuthService authService;

    @MockitoSpyBean
    TokenIssuer tokenIssuer;

    @Test
    void rotationCommitsOldRevocationAndNewSessionInSameFamily() {
        var u=user("rotate");
        var family=UUID.randomUUID();
        var old=refresh(u,"original",family,false,future());
        var request=new MockHttpServletRequest(); request.addHeader("User-Agent","integration-browser");
        var issued=authService.refresh("original",request);

        assertThat(refreshTokenRepository.findById(old.getId()).orElseThrow().isRevoked()).isTrue();
        var replacement=refreshTokenRepository.findByTokenHash(tokenHasher.hash(issued.refreshToken())).orElseThrow();

        assertThat(replacement.getFamilyId()).isEqualTo(family);
        assertThat(replacement.isRevoked()).isFalse();
        assertThat(replacement.getUserAgent()).isEqualTo("integration-browser");
        assertThat(replacement.getTokenHash()).isNotEqualTo(issued.refreshToken());
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void expiredRefreshLeavesSessionUnchanged() {
        var t=refresh(user("expired"),"old",UUID.randomUUID(),false,OffsetDateTime.now().minusDays(1));

        assertThatThrownBy(() -> authService.refresh("old",new MockHttpServletRequest()))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokenRepository.findById(t.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void issuanceFailureRollsBackOldTokenRevocation() {
        var u=user("rollback");
        var family=UUID.randomUUID();
        var t=refresh(u,"original",family,false,future());
        doThrow(new IllegalStateException("issuance failed")).when(tokenIssuer)
                .issueTokenPair(any(),eq(family),any());
        assertThatThrownBy(() -> authService.refresh("original",new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(refreshTokenRepository.findById(t.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @Tag("known-defect")
    void reuseRevocationMustRemainCommittedAfterException() {
        var u=user("reuse");
        var family=UUID.randomUUID();
        refresh(u,"consumed",family,true,future());
        var child=refresh(u,"child",family,false,future());
        var independent=refresh(u,"independent",UUID.randomUUID(),false,future());
        assertThatThrownBy(() -> authService.refresh("consumed",new MockHttpServletRequest()))
                .isInstanceOf(RefreshTokenReuseException.class);
        // Repository read occurs AFTER AuthService's transaction has completed.
        assertThat(refreshTokenRepository.findById(child.getId()).orElseThrow().isRevoked())
                .as("family revocation must survive the reuse exception").isTrue();
        assertThat(refreshTokenRepository.findById(independent.getId()).orElseThrow().isRevoked()).isFalse();
    }
}