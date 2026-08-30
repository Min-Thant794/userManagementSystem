package com.minthanttun.usermanagementsystem.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minthanttun.usermanagementsystem.admin.dto.AuditLogResponse;
import com.minthanttun.usermanagementsystem.audit.AuditAction;
import com.minthanttun.usermanagementsystem.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> listAuditLogs(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) AuditAction action,
            Pageable pageable
    ) {
        Specification<com.minthanttun.usermanagementsystem.audit.AuditLog> spec = Specification.allOf(
                AuditLogSpecifications.hasActor(actorUserId),
                AuditLogSpecifications.hasTarget(targetUserId),
                AuditLogSpecifications.hasAction(action)
        );

        return auditLogRepository.findAll(spec, pageable)
                .map(log -> AuditLogResponse.from(log, objectMapper));
    }
}