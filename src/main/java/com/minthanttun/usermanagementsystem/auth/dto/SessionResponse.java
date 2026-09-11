package com.minthanttun.usermanagementsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record SessionResponse (

        @Schema(
                description = "Unique identifier of the session",
                example = "123"
        )
        Long id,

        @Schema(
                description = "Device or client used to create the session.",
                example = "MacBook Pro - Chrome"
        )
        String device,

        @Schema(
                description = "Approximate location associated with the session.",
                example = "Singapore"
        )
        String location,

        @Schema(
                description = "Timestamp when the session was lat used.",
                example = "2026-09-08T18:45:30+08:00"
        )
        OffsetDateTime lastUsedAt,

        @Schema(
                description = "Timestamp when the session was created.",
                example = "2026-09-08T18:45:30+08:00"
        )
        OffsetDateTime createdAt,

        @Schema(
                description = "Indicates whether this is the session currently being used by the authenticated user.",
                example = "true"
        )
        boolean current
) {
}
