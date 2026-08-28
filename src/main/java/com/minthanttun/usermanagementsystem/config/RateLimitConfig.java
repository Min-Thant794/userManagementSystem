package com.minthanttun.usermanagementsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.minthanttun.usermanagementsystem.config.RateLimitRule.KeyType.IP;
import static com.minthanttun.usermanagementsystem.config.RateLimitRule.KeyType.USER;

@Configuration
public class RateLimitConfig {

    @Bean
    public List<RateLimitRule> rateLimitRules() {
        return List.of(
                new RateLimitRule("/api/auth/login", 5, 5, 60, IP),
                new RateLimitRule("/api/auth/signup", 5, 5, 60, IP),
                new RateLimitRule("/api/auth/forgot-password", 5, 5, 60, IP),
                new RateLimitRule("/api/auth/reset-password", 5, 5, 60, IP),
                new RateLimitRule("/api/users/**", 100, 100, 60, USER),
                new RateLimitRule("/api/admin/**", 100, 100, 60, USER)
        );
    }

    //The global ceiling - one shared bucket across ALL requests, regardless of endpoint or identity.
    public static final int GLOBAL_CAPACITY = 1000;
    public static final long GLOBAL_REFILL_PERIOD_SECONDS = 60;
}
