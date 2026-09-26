package com.minthanttun.usermanagementsystem.user;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class UserTest {
    @ParameterizedTest
    @CsvSource(value = {"testuser,+6591111111,true", "NULL,+6591111111,false", "testuser,NULL,false", "NULL,NULL,false"}, nullValues = "NULL")
    void completenessRequiresBothUsernameAndPhone(String username, String phone, boolean expected) {
        assertThat(User.builder()
                .username(username)
                .phoneNumber(phone)
                .build()
                .isProfileComplete()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "past", "future"})
    void lockDependsOnFutureDeadline(String state) {
        User user = new User();
        if (!state.equals("none")) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(state.equals("future") ? 30 : -30));
        }
        assertThat(user.isLocked()).isEqualTo(state.equals("future"));
    }
}
