package com.minthanttun.usermanagementsystem.admin.dto;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class AdminUserResponseTest {
    @Test
    void mapsAdminFieldsAndTimestamps() {
        UUID id = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime updated = created.plusDays(1), lock = created.plusDays(2);
        User user = User.builder()
                .id(id)
                .username("testuser")
                .email("test@example.com")
                .phoneNumber("+6591111111")
                .role(Role.ADMIN)
                .status(AccountStatus.SUSPENDED)
                .emailVerified(true)
                .failedLoginAttempts(5)
                .lockedUntil(lock)
                .profileImageUrl("https://example.com/avatar.jpg")
                .createdAt(created)
                .updatedAt(updated)
                .build();

        assertThat(AdminUserResponse.from(user))
                .isEqualTo(new AdminUserResponse(
                        id, "testuser", "test@example.com",
                        "+6591111111", Role.ADMIN,
                        AccountStatus.SUSPENDED, true, 5, lock,
                        true, "https://example.com/avatar.jpg",
                        created, updated));
    }

    @ParameterizedTest
    @CsvSource(value = {"testuser,+6591111111,true", "NULL,+6591111111,false", "testuser,NULL,false", "NULL,NULL,false"},
    nullValues = "NULL")
    void derivesProfileCompletenessAndPreservesNullableFields(String username, String phone, boolean complete) {
        User user = User.builder().username(username).phoneNumber(phone).build();
        AdminUserResponse result = AdminUserResponse.from(user);
        assertThat(result.profileComplete()).isEqualTo(complete);
        assertThat(result.username()).isEqualTo(username);
        assertThat(result.phoneNumber()).isEqualTo(phone);
        assertThat(result.lockedUntil()).isNull();
        assertThat(result.profileImageUrl()).isNull();
        assertThat(result.createdAt()).isNull();
        assertThat(result.updatedAt()).isNull();
    }
}
