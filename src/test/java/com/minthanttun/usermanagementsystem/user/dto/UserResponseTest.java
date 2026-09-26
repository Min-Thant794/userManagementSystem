package com.minthanttun.usermanagementsystem.user.dto;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class UserResponseTest {
    @Test
    void mapsPublicFieldsWithoutPasswordOrLockoutState() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .username("testuser")
                .email("old@example.com")
                .pendingEmail("new@example.com")
                .phoneNumber("+6591111111")
                .role(Role.ADMIN)
                .profileImageUrl("https://example.com/avatar.jpg")
                .passwordHash("private-hash")
                .failedLoginAttempts(3)
                .build();

        UserResponse result = UserResponse.from(user);
        assertThat(result).isEqualTo(new UserResponse(id, "testuser", "old@example.com", "new@example.com", "+6591111111", Role.ADMIN, "https://example.com/avatar.jpg"));
        var json = new ObjectMapper().valueToTree(result);
        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.has("failedLoginAttempts")).isFalse();
        assertThat(json.toString()).doesNotContain("private-hash");
    }

    @Test
    void nullableProfileFieldsArePreserved() {
        UserResponse result = UserResponse.from(User.builder().email("oauth@example.com").build());
        assertThat(result.username()).isNull();
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.pendingEmail()).isNull();
        assertThat(result.profileImageUrl()).isNull();
        assertThat(result.email()).isEqualTo("oauth@example.com");
    }
}
