package com.minthanttun.usermanagementsystem.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest (

        @Schema(
                description = "New username for the user. If provided, it must be between 3 and 50 characters and may contain only letters, numbers, and underscores.",
                example = "username",
                minLength = 3,
                maxLength = 50
        )
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "Username may only contain letters, numbers, and underscores"
        )
        String username,

        @Schema(
                description = "New email address for the user.",
                example = "user@example.com",
                format = "email",
                maxLength = 255
        )
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "New phone number for the user. May optionally start with + and must contain 7 to 15 digits.",
                example = "+6591234567"
        )
        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "Phone number must be 7-15 digits, optionally starting with +"
        )
        String phoneNumber
) {
}
