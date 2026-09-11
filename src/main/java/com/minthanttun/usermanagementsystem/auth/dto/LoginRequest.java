package com.minthanttun.usermanagementsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @Schema(
                description = "Username or email address used to authenticate the user",
                example = "exampleUsername",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Username or email is required")
        String identifier,

        @Schema(
                description = "Password associated with the user account",
                example = "SecurePassword123",
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Password is required")
        String password
){
}
