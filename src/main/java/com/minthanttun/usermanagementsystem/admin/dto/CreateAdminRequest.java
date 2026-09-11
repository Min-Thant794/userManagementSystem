package com.minthanttun.usermanagementsystem.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest (

        @Schema(
                description = "Username for the new administrator account.",
                example = "admin_user",
                minLength = 3,
                maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "Username may only contain letters, numbers, and underscores"
        )
        String username,

        @Schema(
                description = "Email address for the new administrator account.",
                example = "admin@example.com",
                format = "email",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "Phone number for the new administrator account.",
                example = "+6591234567",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "Phone number must be 7–15 digits, optionally starting with +"
        )
        String phoneNumber,

        @Schema(
                description = "Password for the new administrator account. Must contain at least 8 characters, including at least one letter and one number.",
                example = "adminPassword123",
                minLength = 8,
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number"
        )
        String password
) {
}
