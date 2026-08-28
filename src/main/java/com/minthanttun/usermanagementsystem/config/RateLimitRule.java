package com.minthanttun.usermanagementsystem.config;

public record RateLimitRule (
        String pathPattern,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        KeyType keyType
) {
    public enum KeyType { IP, USER }
}