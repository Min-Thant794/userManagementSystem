package com.minthanttun.usermanagementsystem.auth.dto;

public record AuthResponse (
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
    }
}
