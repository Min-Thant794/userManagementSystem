package com.minthanttun.usermanagementsystem.e2e;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProfileWorkflowIT extends E2eSupport {
    @Test void loginReadUpdateReadAndLoginWithNewUsername() throws Exception {
        var account=registered("profile"); var session=login(account);
        assertThat(get("/api/users/me",session).expect(200).body().path("username").asText()).isEqualTo("profile");
        api.call("PATCH","/api/users/me",Map.of("username","renamed","phoneNumber","+6591234567"),
                session.access(),null).expect(200);
        var readAgain=get("/api/users/me",session).expect(200);
        assertThat(readAgain.body().path("username").asText()).isEqualTo("renamed");
        assertThat(readAgain.body().path("phoneNumber").asText()).isEqualTo("+6591234567");
        loginResponse("profile",PASSWORD).expect(401);
        tokens(loginResponse("renamed",PASSWORD).expect(200));
        assertThat(jdbc.queryForObject("select username from users where id=?",String.class,account.id())).isEqualTo("renamed");
    }
    @Test void pendingEmailIsConfirmedByDeliveredMailAndRevokesOldSessions() throws Exception {
        var account=registered("emailchange"); var session=login(account);
        String next="newaddress@example.com";
        var pending=api.call("PATCH","/api/users/me",Map.of("email",next),session.access(),null).expect(200);
        assertThat(pending.body().path("email").asText()).isEqualTo(account.email());
        assertThat(pending.body().path("pendingEmail").asText()).isEqualTo(next);
        verify(inbox.awaitToken(next,"Verify your email address","/verify"));
        var updated=get("/api/users/me",session).expect(200);
        assertThat(updated.body().path("email").asText()).isEqualTo(next);
        assertThat(updated.body().path("pendingEmail").isNull()).isTrue();
        assertThat(revoked(session.refresh())).isTrue();
        refresh(session.refresh()).expect(401);
        loginResponse(account.email(),PASSWORD).expect(401);
        get("/api/users/me",tokens(loginResponse(next,PASSWORD).expect(200))).expect(200);
    }
}