package com.minthanttun.usermanagementsystem.e2e;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionWorkflowIT extends E2eSupport {
    @Test void loginRefreshAndUseReplacementTokens() throws Exception {
        var account=registered("rotation"); var original=login(account);
        var next=tokens(refresh(original.refresh()).expect(200));
        assertThat(next.refresh()).as("Refresh rotation must issue a different token; add a unique JWT jti")
                .isNotEqualTo(original.refresh());
        get("/api/users/me",next).expect(200);
        assertThat(revoked(original.refresh())).isTrue(); assertThat(revoked(next.refresh())).isFalse();
        var sessions=get("/api/auth/sessions",next).expect(200).body();
        assertThat(sessions.size()).isEqualTo(1);
        assertThat(sessions.get(0).path("current").asBoolean()).isTrue();
    }
    @Test void replayKillsTheRotatedFamilyButNotAnIndependentLogin() throws Exception {
        var account=registered("replay");
        var original=login(account); var independent=login(account);
        assertThat(independent.refresh()).as("Separate logins require distinct refresh tokens").isNotEqualTo(original.refresh());
        var next=tokens(refresh(original.refresh()).expect(200));
        refresh(original.refresh()).expect(401);
        assertThat(revoked(next.refresh())).as("Family revocation must commit despite the reuse exception").isTrue();
        refresh(next.refresh()).expect(401);
        assertThat(revoked(independent.refresh())).isFalse();
        get("/api/users/me",tokens(refresh(independent.refresh()).expect(200))).expect(200);
    }
    @Test void logoutClearsCookieAndBlocksRefreshButAccessJwtKeepsItsCurrentLifetime() throws Exception {
        var account=registered("logout"); var session=login(account);
        var logout=api.call("POST","/api/auth/logout",null,null,session.refresh()).expect(204);
        assertThat(logout.cookieHeader()).contains("Max-Age=0");
        assertThat(logout.refreshCookie()).isEmpty(); assertThat(revoked(session.refresh())).isTrue();
        refresh(session.refresh()).expect(401);
        // This characterizes the current stateless access-token policy, not immediate access revocation.
        get("/api/users/me",session).expect(200);
        api.call("POST","/api/auth/logout",null,null,null).expect(204);
    }
    @Test void revokeOtherSessionsKeepsCurrentAndRespectsUserOwnership() throws Exception {
        var alice=registered("alice"); var bob=registered("bob");
        var current=login(alice); var old=login(alice); var bobs=login(bob);
        assertThat(current.refresh()).isNotEqualTo(old.refresh());
        var bobsSession=get("/api/auth/sessions",bobs).expect(200).body().get(0).path("id").asLong();
        api.call("DELETE","/api/auth/sessions/"+bobsSession,null,current.access(),current.refresh()).expect(404);
        assertThat(revoked(bobs.refresh())).isFalse();
        api.call("DELETE","/api/auth/sessions/others",null,current.access(),current.refresh()).expect(204);
        assertThat(revoked(current.refresh())).isFalse(); assertThat(revoked(old.refresh())).isTrue();
        assertThat(revoked(bobs.refresh())).isFalse();
        refresh(old.refresh()).expect(401);
        get("/api/users/me",tokens(refresh(current.refresh()).expect(200))).expect(200);
    }
}