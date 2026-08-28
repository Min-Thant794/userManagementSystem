package com.minthanttun.usermanagementsystem.config;

public record RateLimitRule (
        String pathPattern,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        KeyType keyType,
        RefillStrategy refillStrategy
) {
    public enum KeyType { IP, USER }
    public enum RefillStrategy { GREEDY, INTERVALLY }
}