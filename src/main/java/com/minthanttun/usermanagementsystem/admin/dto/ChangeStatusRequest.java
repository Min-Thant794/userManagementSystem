package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest (
        @NotNull(message = "Status is required")
        AccountStatus status
) {
}
