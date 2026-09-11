package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse (

        @Schema(
                description = "Unique identifier of the user.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID id,

        @Schema(
                description = "Unique username of the user",
                example = "uniqueUsername"
        )
        String username,

        @Schema(
                description = "Email address associated with the user account.",
                example = "useremail@example.com"
        )
        String email,

        @Schema(
                description = "Phone number associated with the user account."
        )
        String phoneNumber,

        @Schema(
                description = "Role assigned to the user.",
                example = "USER"
        )
        Role role,

        @Schema(
                description = "Current account status.",
                example = "ACTIVE"
        )
        AccountStatus status,

        @Schema(
                description = "Indicates whether the user's email address has been verified.",
                example = "true"
        )
        boolean emailVerified,

        @Schema(
                description = "Number of consecutive failed login attempts",
                example = "0"
        )
        int failedLoginAttempts,

        @Schema(
                description = "Timestamp until which the account remains locked. Null when the account is not currently locked.",
                example = "2026-09-08T20:30:00+08:00",
                nullable = true
        )
        OffsetDateTime lockedUntil,

        @Schema(
                description = "Indicates whether the user has completed all required profile information.",
                example = "true"
        )
        boolean profileComplete,

        @Schema(
                description = "URL of the user's profile image. Null when no profile image has been uploaded.",
                example = "https://res.cloudinary.com/example/image/upload/profile-images/550e8400-e29b-41d4-a716-446655440000/avatar"
        )
        String profileImageUrl,

        @Schema(
                description = "Timestamp when the user account was created.",
                example = "2026-08-20T10:15:30+08:00"
        )
        OffsetDateTime createdAt,

        @Schema(
                description = "Timestamp when the user account was created.",
                example = "2026-08-20T10:15:30+08:00"
        )
        OffsetDateTime updatedAt
) {
    public static AdminUserResponse from (User user) {
        return new AdminUserResponse(
          user.getId(),
          user.getUsername(),
          user.getEmail(),
          user.getPhoneNumber(),
          user.getRole(),
          user.getStatus(),
          user.isEmailVerified(),
          user.getFailedLoginAttempts(),
          user.getLockedUntil(),
          user.isProfileComplete(),
          user.getProfileImageUrl(),
          user.getCreatedAt(),
          user.getUpdatedAt()
        );
    }
}
