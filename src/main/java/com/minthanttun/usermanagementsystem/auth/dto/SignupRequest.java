package com.minthanttun.usermanagementsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest (

        @Schema(
                description = "Unique username for the user account",
                example = "john_doe",
                minLength = 3,
                maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "Username may only contain letters, numbers, and underscores"
        )
        String username,

        @Schema(
                description = "Email address associated with the user account",
                example = "john.doe@example.com",
                maxLength = 255,
                format = "email",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "User's phone number in international or local numeric format",
                example = "+6591234567",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "Phone number must be 7-15 digits, optionally starting with +"
        )
        String phoneNumber,

        @Schema(
                description = "Password for the user account. Must contain at least 8 characters, including at least one letter and one number.",
                example = "SecurePassword123",
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
