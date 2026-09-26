package com.minthanttun.usermanagementsystem.security;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class CustomUserDetailsTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    void mapsRoleAndLocalCredentials(Role role) {
        User user = User.builder().username("testuser").passwordHash("hash").role(role).build();
        var details = new CustomUserDetails(user);

        assertThat(details.getUsername()).isEqualTo("testuser");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getUser()).isSameAs(user);
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVE,none,true,true",
            "ACTIVE,past,true,true",
            "ACTIVE,future,true,false",
            "SUSPENDED,future,false,false"
    })
    void accountFlagsReflectStatusAndTemporaryLock(AccountStatus status, String lock, boolean enabled, boolean nonLocked) {
        User user = User.builder().status(status).build();

        if(!lock.equals("none")) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(lock.equals("future") ? 30 : -30));
        }

        var details = new CustomUserDetails(user);
        assertThat(details.isEnabled()).isEqualTo(enabled);
        assertThat(details.isAccountNonLocked()).isEqualTo(nonLocked);
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }
}
