package com.minthanttun.usermanagementsystem.e2e;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationWorkflowIT extends E2eSupport {
    @Test void signupEmailVerificationThenLogin() throws Exception {
        var account=signup("newmember");
        var denied=loginResponse(account.email(),PASSWORD).expect(403);
        assertThat(denied.body().path("action").asText()).isEqualTo("resend_verification");

        String link=verificationLink(account); // From real SMTP mail captured in Mailpit.
        verify(link);
        var session=login(account);
        var profile=get("/api/users/me",session).expect(200);
        assertThat(profile.body().path("id").asText()).isEqualTo(account.id().toString());
        assertThat(profile.body().path("email").asText()).isEqualTo(account.email());
        assertThat(jdbc.queryForObject("select email_verified from users where id=?",Boolean.class,account.id())).isTrue();
        assertThat(refreshTokens.count()).isEqualTo(1);
    }
    @Test void verificationLinkIsSingleUse() throws Exception {
        var account=signup("singleuse"); String link=verificationLink(account); verify(link);
        api.call("POST","/api/auth/verify-email",Map.of("token",link),null,null).expect(401);
        get("/api/users/me",login(account)).expect(200);
    }
    @Test void resendInvalidatesOldVerificationLink() throws Exception {
        var account=signup("resend"); String oldLink=verificationLink(account);
        api.call("POST","/api/auth/resend-verification",Map.of("email",account.email()),null,null).expect(204);
        String replacement=verificationLink(account);
        assertThat(replacement).isNotEqualTo(oldLink);
        api.call("POST","/api/auth/verify-email",Map.of("token",oldLink),null,null).expect(401);
        verify(replacement);
        get("/api/users/me",login(account)).expect(200);
    }
}