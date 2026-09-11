package com.minthanttun.usermanagementsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(

        @Schema(
                description = "JWT access token used to authenticate API requests.",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String accessToken,

        @Schema(
                description = "Authentication scheme used with the access token.",
                example = "Bearer"
        )
        String tokenType,

        @Schema(
                description = "Number of seconds until the access token expires.",
                example = "900"
        )
        long expiresIn
) {
    public static AuthResponse of(String accessToken, long expiresInMs) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs / 1000);
    }
}