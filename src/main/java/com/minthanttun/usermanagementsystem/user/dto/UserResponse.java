package com.minthanttun.usermanagementsystem.user.dto;

import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String phoneNumber,
        Role role,
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.getProfileImageUrl()
        );
    }
}