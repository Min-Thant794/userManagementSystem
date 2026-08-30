package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.audit.AuditAction;
import com.minthanttun.usermanagementsystem.audit.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> hasActor(UUID actoruserId) {
        return ((root, query, cb) -> actoruserId == null ? null : cb.equal(root.get("actorUserId"), actoruserId));
    }

    public static Specification<AuditLog> hasTarget(UUID targetUserId) {
        return ((root, query, cb) -> targetUserId == null ? null : cb.equal(root.get("targetUserId"), targetUserId));
    }

    public static Specification<AuditLog> hasAction(AuditAction action) {
        return ((root, query, cb) -> action == null ? null : cb.equal(root.get("action"), action));
    }
}
