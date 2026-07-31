package com.minthanttun.usermanagementsystem.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest (

        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "Username may only contain letters, numbers, and underscores"
        )
        String username,

        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "Phone number must be 7-15 digits, optionally starting with +"
        )
        String phoneNumber
) {
}
