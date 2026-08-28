package com.minthanttun.usermanagementsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.minthanttun.usermanagementsystem.config.RateLimitRule.KeyType.IP;
import static com.minthanttun.usermanagementsystem.config.RateLimitRule.KeyType.USER;
import static com.minthanttun.usermanagementsystem.config.RateLimitRule.RefillStrategy.GREEDY;
import static com.minthanttun.usermanagementsystem.config.RateLimitRule.RefillStrategy.INTERVALLY;

@Configuration
public class RateLimitConfig {

    @Bean
    public List<RateLimitRule> rateLimitRules() {
        return List.of(
                new RateLimitRule("/api/auth/login", 5, 5, 60, IP, INTERVALLY),
                new RateLimitRule("/api/auth/signup", 5, 5, 60, IP, INTERVALLY),
                new RateLimitRule("/api/auth/forgot-password", 5, 5, 60, IP, INTERVALLY),
                new RateLimitRule("/api/auth/reset-password", 5, 5, 60, IP, INTERVALLY),
                new RateLimitRule("/api/auth/refresh", 20, 20, 60, USER, GREEDY),
                new RateLimitRule("/api/users/**", 100, 100, 60, USER, GREEDY),
                new RateLimitRule("/api/admin/**", 100, 100, 60, USER, GREEDY)
        );
    }

    //The global ceiling - one shared bucket across ALL requests, regardless of endpoint or identity.
    public static final int GLOBAL_CAPACITY = 1000;
    public static final long GLOBAL_REFILL_PERIOD_SECONDS = 60;
}
