package com.minthanttun.usermanagementsystem.auth.dto;

import java.time.OffsetDateTime;

public record SessionResponse (
        Long id,
        String device,
        String location,
        OffsetDateTime lastUsedAt,
        OffsetDateTime createdAt,
        boolean current
) {
}
