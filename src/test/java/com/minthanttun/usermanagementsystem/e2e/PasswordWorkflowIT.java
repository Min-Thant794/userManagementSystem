package com.minthanttun.usermanagementsystem.e2e;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordWorkflowIT extends E2eSupport {
    @Test void deliveredResetLinkChangesPasswordOnceAndRevokesAllPriorSessions() throws Exception {
        var account=registered("reset"); var first=login(account); var second=login(account);
        assertThat(first.refresh()).isNotEqualTo(second.refresh());
        api.call("POST","/api/auth/forgot-password",Map.of("email",account.email()),null,null).expect(204);
        String reset=inbox.awaitToken(account.email(),"Reset your password","/reset");
        api.call("POST","/api/auth/reset-password",Map.of("token",reset,"newPassword","ChangedPassword123"),null,null).expect(204);
        loginResponse(account.email(),PASSWORD).expect(401);
        get("/api/users/me",tokens(loginResponse(account.email(),"ChangedPassword123").expect(200))).expect(200);
        assertThat(revoked(first.refresh())).isTrue(); assertThat(revoked(second.refresh())).isTrue();
        refresh(first.refresh()).expect(401); refresh(second.refresh()).expect(401);
        api.call("POST","/api/auth/reset-password",Map.of("token",reset,"newPassword","AnotherPassword123"),null,null).expect(401);
    }
    @Test void authenticatedPasswordChangeRevokesExistingRefreshSessions() throws Exception {
        var account=registered("changepassword"); var current=login(account); var other=login(account);
        assertThat(current.refresh()).isNotEqualTo(other.refresh());
        api.call("PUT","/api/users/me/password",Map.of("currentPassword",PASSWORD,"newPassword","ChangedPassword123"),
                current.access(),null).expect(204);
        loginResponse(account.email(),PASSWORD).expect(401);
        get("/api/users/me",tokens(loginResponse(account.email(),"ChangedPassword123").expect(200))).expect(200);
        assertThat(revoked(current.refresh())).isTrue(); assertThat(revoked(other.refresh())).isTrue();
        refresh(current.refresh()).expect(401); refresh(other.refresh()).expect(401);
    }
}