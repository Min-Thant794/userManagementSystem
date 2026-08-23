package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest (
        @NotNull(message = "Role is required")
        Role role
) {
}
