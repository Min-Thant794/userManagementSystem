package com.minthanttun.usermanagementsystem.security.ratelimit;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.databind.*;
import com.minthanttun.usermanagementsystem.config.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.user.User;
import io.github.bucket4j.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("remaining-unit")
class RateLimitFilterTest {
    private static final String IP_A = "192.0.2.1";
    private static final String IP_B = "192.0.2.2";
    private static final TimeMeter FROZEN_TIME = new TimeMeter() {
        public long currentTimeNanos() { return 0; }
        public boolean isWallClockBased() { return false; }
    };

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitRule rule(String path, int capacity, RateLimitRule.KeyType key) {
        // A long interval prevents real-time endpoint refill during these no-sleep tests.
        return new RateLimitRule(path, capacity, capacity, 86_400, key, RateLimitRule.RefillStrategy.INTERVALLY);
    }

    private RateLimitFilter filter(int globalCapacity, RateLimitRule... rules) {
        var filter = new RateLimitFilter(List.of(rules));
        Bucket global = Bucket.builder().withCustomTimePrecision(FROZEN_TIME)
                .addLimit(limit -> limit.capacity(globalCapacity).refillGreedy(globalCapacity, Duration.ofMinutes(1)))
                .build();
        // Inject a real bucket with frozen time; no production source change is necessary.
        ReflectionTestUtils.setField(filter, "globalBucket", global);
        return filter;
    }

    private record Attempt(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain) {}

    private Attempt request(RateLimitFilter filter, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        return new Attempt(request, response, chain);
    }

    private void allowed(Attempt attempt) throws Exception {
        assertThat(attempt.response().getStatus()).isEqualTo(200);
        verify(attempt.chain()).doFilter(attempt.request(), attempt.response());
        verifyNoMoreInteractions(attempt.chain());
    }

    private void rejected(Attempt attempt, String detail) throws Exception {
        assertThat(attempt.response().getStatus()).isEqualTo(429);
        assertThat(attempt.response().getHeader("Retry-After")).isEqualTo("60");
        assertThat(attempt.response().getContentType()).isEqualTo("application/json");
        JsonNode body = new ObjectMapper().readTree(attempt.response().getContentAsString());
        assertThat(body.path("type").asText()).isEqualTo("about:blank");
        assertThat(body.path("title").asText()).isEqualTo("Too Many Requests");
        assertThat(body.path("status").asInt()).isEqualTo(429);
        assertThat(body.path("detail").asText()).isEqualTo(detail);
        verifyNoInteractions(attempt.chain());
    }

    private void authenticate(UUID userId) {
        var principal = new CustomUserDetails(User.builder().id(userId).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void globalBucketStartsWithConfiguredCapacity() {
        var filter = new RateLimitFilter(List.of());
        Bucket global = (Bucket) ReflectionTestUtils.getField(filter, "globalBucket");
        assertThat(global.getAvailableTokens()).isEqualTo(RateLimitConfig.GLOBAL_CAPACITY);
    }

    @ParameterizedTest
    @EnumSource(RateLimitRule.RefillStrategy.class)
    void requestsUpToEndpointCapacityPassAndNextOneIsRejected(RateLimitRule.RefillStrategy strategy) throws Exception {
        var rule = new RateLimitRule("/api/auth/login", 2, 2, 86_400, RateLimitRule.KeyType.IP, strategy);
        var filter = filter(100, rule);
        allowed(request(filter, "/api/auth/login", IP_A));
        allowed(request(filter, "/api/auth/login", IP_A));
        rejected(request(filter, "/api/auth/login", IP_A), "Rate limit exceeded for this endpoint");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/unmatched"})
    void globalLimitAppliesToMatchingAndUnmatchedPaths(String path) throws Exception {
        var filter = filter(2, rule("/api/auth/login", 10, RateLimitRule.KeyType.IP));
        allowed(request(filter, path, IP_A));
        allowed(request(filter, path, IP_B));
        rejected(request(filter, path, "192.0.2.3"), "Global rate limit exceeded, try again shortly");
    }

    @Test
    void differentIpsHaveSeparateEndpointBuckets() throws Exception {
        var filter = filter(100, rule("/api/auth/login", 1, RateLimitRule.KeyType.IP));
        allowed(request(filter, "/api/auth/login", IP_A));
        rejected(request(filter, "/api/auth/login", IP_A), "Rate limit exceeded for this endpoint");
        allowed(request(filter, "/api/auth/login", IP_B));
    }

    @Test
    void differentUsersOnSameIpHaveSeparateBuckets() throws Exception {
        var filter = filter(100, rule("/api/users/**", 1, RateLimitRule.KeyType.USER));
        UUID first = UUID.randomUUID();
        authenticate(first);
        allowed(request(filter, "/api/users/me", IP_A));
        rejected(request(filter, "/api/users/me", IP_A), "Rate limit exceeded for this endpoint");
        authenticate(UUID.randomUUID());
        allowed(request(filter, "/api/users/me", IP_A));
    }

    @Test void sameUserChangingIpStillSharesBucket() throws Exception {
        var filter = filter(100, rule("/api/users/**", 1, RateLimitRule.KeyType.USER));
        authenticate(UUID.randomUUID());
        allowed(request(filter, "/api/users/me", IP_A));
        rejected(request(filter, "/api/users/me", IP_B), "Rate limit exceeded for this endpoint");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void userRuleFallsBackToIpWithoutCustomUserPrincipal(boolean otherPrincipal) throws Exception {
        if (otherPrincipal) SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));
        var filter = filter(100, rule("/api/users/**", 1, RateLimitRule.KeyType.USER));
        allowed(request(filter, "/api/users/me", IP_A));
        rejected(request(filter, "/api/users/me", IP_A), "Rate limit exceeded for this endpoint");
        allowed(request(filter, "/api/users/me", IP_B));
    }

    @Test
    void firstMatchingRuleWins() throws Exception {
        var filter = filter(100,
                rule("/api/auth/login", 2, RateLimitRule.KeyType.IP),
                rule("/api/**", 1, RateLimitRule.KeyType.IP));
        allowed(request(filter, "/api/auth/login", IP_A));
        allowed(request(filter, "/api/auth/login", IP_A));
        rejected(request(filter, "/api/auth/login", IP_A), "Rate limit exceeded for this endpoint");
    }

    @Test
    void endpointBucketsAreSeparatedByRulePattern() throws Exception {
        var filter = filter(100,
                rule("/api/auth/login", 1, RateLimitRule.KeyType.IP),
                rule("/api/auth/signup", 1, RateLimitRule.KeyType.IP));
        allowed(request(filter, "/api/auth/login", IP_A));
        allowed(request(filter, "/api/auth/signup", IP_A));
        rejected(request(filter, "/api/auth/login", IP_A), "Rate limit exceeded for this endpoint");
    }

    @Test
    void pathsUnderOneWildcardRuleShareItsBucket() throws Exception {
        var filter = filter(100, rule("/api/users/**", 1, RateLimitRule.KeyType.IP));
        allowed(request(filter, "/api/users/me", IP_A));
        rejected(request(filter, "/api/users/me/photo", IP_A), "Rate limit exceeded for this endpoint");
    }
}