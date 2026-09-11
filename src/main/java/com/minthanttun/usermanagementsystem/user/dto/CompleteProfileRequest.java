package com.minthanttun.usermanagementsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteProfileRequest (

        @Schema(
                description = "Username to assign to the user account when completing the profile. Must be between 3 and 50 characters and may contain only letters, numbers, and underscores.",
                example = "userName123",
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
                description = "Phone number to associate with the user account. May optionally start with + and must contain 7 to 15 digits.",
                example = "+6591234567",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "Phone number must be 7-15 digits, optionally starting with +"
        )
        String phoneNumber
) {
}
