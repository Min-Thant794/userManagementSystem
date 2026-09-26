package com.minthanttun.usermanagementsystem.security.oauth2;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.oauth2.core.oidc.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class CustomOAuth2UserTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    void exposeLocalIdentityRoleAndProviderClaims(Role role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .role(role)
                .build();

        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        var token = new OidcIdToken("token", issued, issued.plusSeconds(3600),
                Map.of("sub", "provider-subject", "email", "test@example.com"));
        var info = new OidcUserInfo(Map.of("sub", "provider-subject", "name", "testuser"));
        var principal = new CustomOAuth2User(user, token, info);
        assertThat(principal.getUser()).isSameAs(user);
        assertThat(principal.getName()).isEqualTo(user.getId().toString());
        assertThat(principal.getSubject()).isEqualTo("provider-subject");
        assertThat(principal.getAttributes()).isEqualTo(token.getClaims());
        assertThat(principal.getClaims()).isEqualTo(token.getClaims());
        assertThat(principal.getIdToken()).isSameAs(token);
        assertThat(principal.getUserInfo()).isSameAs(info);
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_" + role.name());
    }

    @Test
    void absentUserInfoRemainsNull() {
        var principal = new CustomOAuth2User(User.builder().build(),
                new OidcIdToken("token", Instant.now(),
                        Instant.now().plusSeconds(3600), Map.of("sub", "subject")), null);
        assertThat(principal.getUserInfo()).isNull();;
        assertThat(principal.getSubject()).isEqualTo("subject");
    }
}
