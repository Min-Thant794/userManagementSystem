package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.common.exception.RefreshTokenReuseException;
import com.minthanttun.usermanagementsystem.security.jwt.TokenIssuer;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshConcurrencyIT extends IntegrationSupport {
    @Autowired
    AuthService authService;

    @MockitoSpyBean RefreshTokenRepository refreshTokenRepository;

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10,TimeUnit.SECONDS);
        }
        catch(Exception e) {
            throw new IllegalStateException("workers did not reach barrier",e);
        }
    }

    @Test
    @Timeout(40)
    void postgresConditionalUpdateAllowsExactlyOneWinner() throws Exception {
        var token=refresh(user("atomic"),"raw",UUID.randomUUID(),false,future());
        var barrier=new CyclicBarrier(2);
        var executor=Executors.newFixedThreadPool(2);

        try {
            Callable<Integer> task=() -> tx().execute(status -> {
                assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked()).isFalse();
                await(barrier); // Both transactions have read the original state.
                return refreshTokenRepository.revokeIfActive(token.getId());
            });

            var one=executor.submit(task); var two=executor.submit(task);

            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1,0);
            assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked()).isTrue();
        } finally {
            executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS);
        }
    }

    @Test
    @Tag("known-defect")
    @Timeout(40)
    void simultaneousRefreshHasOneSuccessOneReuseAndNoSurvivingFamilySession() throws Exception {
        var family=UUID.randomUUID();
        refresh(user("race"),"original",family,false,future());

        var barrier=new CyclicBarrier(2);
        // The spy only coordinates timing. The underlying lookup and update still use PostgreSQL.
        doAnswer(invocation -> {
            Object found=invocation.callRealMethod(); await(barrier); return found;
        }).when(refreshTokenRepository).findByTokenHash(tokenHasher.hash("original"));

        var executor=Executors.newFixedThreadPool(2);

        try {
            Callable<Object> task=() -> {
                try {
                    return authService.refresh("original",new MockHttpServletRequest());
                }
                catch(RefreshTokenReuseException expected) {
                    return expected;
                }
            };

            var one=executor.submit(task); var two=executor.submit(task);
            var results=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));

            assertThat(results.stream().filter(TokenIssuer.IssuedTokens.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(RefreshTokenReuseException.class::isInstance).count()).isEqualTo(1);

            var rows=refreshTokenRepository.findAll();

            assertThat(rows).hasSize(2); // Original plus exactly one replacement.
            assertThat(rows).allMatch(t -> family.equals(t.getFamilyId()));
            assertThat(rows).allMatch(RefreshToken::isRevoked);
        } finally {
            executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS);
        }
    }
}