package com.minthanttun.usermanagementsystem.security.ratelimit;

import com.minthanttun.usermanagementsystem.config.RateLimitConfig;
import com.minthanttun.usermanagementsystem.config.RateLimitRule;
import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final List<RateLimitRule> rateLimitRules;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final ConcurrentHashMap<String, Bucket> endpointBuckets = new ConcurrentHashMap<>();
    private final Bucket globalBucket = buildBucket(
            RateLimitConfig.GLOBAL_CAPACITY,
            RateLimitConfig.GLOBAL_CAPACITY,
            RateLimitConfig.GLOBAL_REFILL_PERIOD_SECONDS
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        //1. global ceiling - checked on every request, no exceptions.
        if (!globalBucket.tryConsume(1)) {
            rejectWithTooManyRequests(response, "Global rate limit exceeded, try again shortly");
            return;
        }

        //2. Endpoint-specific rule, if this path matches one.
        RateLimitRule matchedRule = findMatchingRule(request.getRequestURI());
        if (matchedRule != null) {
            String key = resolveKey(request, matchedRule.keyType());
            Bucket bucket = endpointBuckets.computeIfAbsent(
                    matchedRule.pathPattern() + ":" + key,
                    k -> buildBucket(matchedRule.capacity(), matchedRule.refillTokens(), matchedRule.refillPeriodSeconds())
            );

            if (!bucket.tryConsume(1)) {
                rejectWithTooManyRequests(response, "Rate limit exceeded for this endpoint");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule findMatchingRule(String uri) {
        return rateLimitRules.stream()
                .filter(rule -> pathMatcher.match(rule.pathPattern(), uri))
                .findFirst()
                .orElse(null);
    }

    private String resolveKey(HttpServletRequest request, RateLimitRule.KeyType keyType) {
        if (keyType == RateLimitRule.KeyType.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                return "user:" + userDetails.getUser().getId();
            }
            //no authenticated user (e.g., a bad/missing token hit a USER-tier endpoint) - fall back to IP
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket buildBucket(int capacity, int refillTokens, long refillPeriodSeconds) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofSeconds(refillPeriodSeconds)))
                .build();
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"" + message + "\"}"
        );
    }
}
