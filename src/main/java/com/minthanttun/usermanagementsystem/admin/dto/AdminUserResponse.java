package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse (
        UUID id,
        String username,
        String email,
        String phoneNumber,
        Role role,
        AccountStatus status,
        boolean emailVerified,
        int failedLoginAttempts,
        OffsetDateTime lockedUntil,
        boolean profileComplete,
        String profileImageUrl,
        OffsetDateTime createdAt,
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
