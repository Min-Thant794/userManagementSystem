package com.minthanttun.usermanagementsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetInitialPasswordRequest (

        @Schema(
                description = "Initial password to set for the user account. Must be at least 8 characters long and contain at least one letter and one number.",
                example = "InitialPassword123",
                minLength = 8,
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number"
        )
        String newPassword
) {
}
