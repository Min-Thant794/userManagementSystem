package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest (

        @Schema(
                description = "New account status to assign to the user.",
                example = "ACTIVE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Status is required")
        AccountStatus status
) {
}
