package com.minthanttun.usermanagementsystem.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse of(String accessToken, long expiresInMs) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs / 1000);
    }
}