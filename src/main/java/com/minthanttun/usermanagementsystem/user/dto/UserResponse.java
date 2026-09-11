package com.minthanttun.usermanagementsystem.user.dto;

import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UserResponse(

        @Schema(
                description = "Unique identifier of the user account.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID id,

        @Schema(
                description = "Unique username of the user.",
                example = "uniqueUsername"
        )
        String username,

        @Schema(
                description = "Current email address associated with the user account",
                example = "emailaddress@example.com",
                format = "email"
        )
        String email,

        @Schema(
                description = "New email address awaiting verification. Null when there is no pending email change.",
                example = "newemailaddress@example.com",
                format = "email",
                nullable = true
        )
        String pendingEmail,

        @Schema(
                description = "Phone number associated with the user account.",
                example = "+6591234567"
        )
        String phoneNumber,

        @Schema(
                description = "Role assigned to the user account.",
                example = "USER"
        )
        Role role,

        @Schema(
                description = "URL of the user's profile image. Null when no profile image has been uploaded.",
                example = "https://res.cloudinary.com/example/image/upload/profile-images/550e8400-e29b-41d4-a716-446655440000/avatar",
                nullable = true
        )
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getPendingEmail(),
                user.getPhoneNumber(), user.getRole(), user.getProfileImageUrl()
        );
    }
}