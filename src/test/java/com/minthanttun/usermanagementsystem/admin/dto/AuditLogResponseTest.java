package com.minthanttun.usermanagementsystem.admin.dto;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minthanttun.usermanagementsystem.audit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
class AuditLogResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void mapsIdentityActionTimestampAndNestedJson() {
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        AuditLog log = AuditLog.builder().id(4L).actorUserId(actor).targetUserId(target).action(AuditAction.UPDATE)
                .createdAt(created).details("{\"before\":{\"username\":\"testuser\"},\"after\":{\"username\":\"testuser2\"}}").build();
        assertThat(AuditLogResponse.from(log, mapper)).isEqualTo(new AuditLogResponse(4L, actor, target, AuditAction.UPDATE,
                Map.of("before", Map.of("username", "testuser"), "after", Map.of("username", "testuser2")), created));
    }
    @Test void nullDetailsBecomeEmptyMap() {
        assertThat(AuditLogResponse.from(AuditLog.builder().details(null).build(), mapper).details()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"broken-json", "[]", "123"})
    void unreadableObjectDetailsPreserveRawValue(String json) {
        assertThat(AuditLogResponse.from(AuditLog.builder().details(json).build(), mapper).details())
                .containsExactlyEntriesOf(Map.of("raw", json));
    }
}