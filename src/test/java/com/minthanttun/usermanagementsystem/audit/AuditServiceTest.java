package com.minthanttun.usermanagementsystem.audit;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("remaining-unit")
public class AuditServiceTest {
    @Test
    void savesActorTargetActionAndSerializedDetails() throws Exception {
        AuditLogRepository logs = mock(AuditLogRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        var service = new AuditService(logs, mapper);
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
        Map<String, Object> details = Map.of("before", "USER", "after", "ADMIN");
        service.log(actor, target, AuditAction.ROLE_CHANGE, details);
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getActorUserId()).isEqualTo(actor);
        assertThat(saved.getValue().getTargetUserId()).isEqualTo(target);
        assertThat(saved.getValue().getAction()).isEqualTo(AuditAction.ROLE_CHANGE);
        assertThat(mapper.readTree(saved.getValue().getDetails())).isEqualTo(mapper.valueToTree(details));
    }

    @Test
    void serializationFailureStillSavesEntryWithEmptyObject() throws Exception {
        AuditLogRepository logs = mock(AuditLogRepository.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        Map<String, Object> details = Map.of("value", "example");
        when(mapper.writeValueAsString(details)).thenThrow(new JsonProcessingException("serialization failed") {});
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
        new AuditService(logs, mapper).log(actor, target, AuditAction.UPDATE, details);
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(saved.capture());
        assertThat(saved.getValue().getDetails()).isEqualTo("{}");
        assertThat(saved.getValue().getActorUserId()).isEqualTo(actor);
        assertThat(saved.getValue().getTargetUserId()).isEqualTo(target);
        assertThat(saved.getValue().getAction()).isEqualTo(AuditAction.UPDATE);
    }
}
