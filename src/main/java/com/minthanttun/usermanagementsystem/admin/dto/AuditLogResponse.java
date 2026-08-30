package com.minthanttun.usermanagementsystem.admin.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minthanttun.usermanagementsystem.audit.AuditAction;
import com.minthanttun.usermanagementsystem.audit.AuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID actorUserId,
        UUID targetUserId,
        AuditAction action,
        Map<String, Object> details,
        OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log, ObjectMapper objectMapper) {
        Map<String, Object> parsedDetails;
        try {
            parsedDetails = log.getDetails() == null
                    ? Map.of()
                    : objectMapper.readValue(log.getDetails(), Map.class);
        } catch (Exception e) {
            parsedDetails = Map.of("raw", log.getDetails());
        }

        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                log.getTargetUserId(),
                log.getAction(),
                parsedDetails,
                log.getCreatedAt()
        );
    }
}