package com.minthanttun.usermanagementsystem.httpIntegration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionControllerHttpIT extends HttpIntegrationSupport {
    @Test void listsOnlyOwnActiveSessionsAndMarksCurrent() throws Exception {
        var alice=user("alice"); var bob=user("bob");
        var mine=session(alice,"current"); session(bob,"other");
        var revoked=session(alice,"revoked"); revoked.setRevoked(true); refreshTokens.saveAndFlush(revoked);
        mvc.perform(bearer(get("/api/auth/sessions").cookie(new Cookie("refreshToken","current")),alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId().intValue()))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }
    @Test void cannotRevokeAnotherUsersSession() throws Exception {
        var alice=user("alice"); var bobs=session(user("bob"),"bobs");
        mvc.perform(bearer(delete("/api/auth/sessions/"+bobs.getId()),alice))
                .andExpect(problem(404,"Resource Not Found"));
        assertThat(refreshTokens.findById(bobs.getId()).orElseThrow().isRevoked()).isFalse();
    }
    @Test void canRevokeOwnSession() throws Exception {
        var u=user("alice"); var s=session(u,"mine");
        mvc.perform(bearer(delete("/api/auth/sessions/"+s.getId()),u)).andExpect(status().isNoContent());
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isTrue();
    }
    @Test void revokeOthersPreservesCurrentAndAnotherUsersSessions() throws Exception {
        var u=user("alice"); var current=session(u,"current"); var old=session(u,"old");
        var unrelated=session(user("bob"),"other");
        mvc.perform(bearer(delete("/api/auth/sessions/others").cookie(new Cookie("refreshToken","current")),u))
                .andExpect(status().isNoContent());
        assertThat(refreshTokens.findById(current.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(refreshTokens.findById(old.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findById(unrelated.getId()).orElseThrow().isRevoked()).isFalse();
    }
    @Test void revokeOthersWithoutCookieRevokesAllOwnSessions() throws Exception {
        var u=user("alice"); var s=session(u,"mine");
        mvc.perform(bearer(delete("/api/auth/sessions/others"),u)).andExpect(status().isNoContent());
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isTrue();
    }
    @Test void anonymousSessionRequestCannotReadOrDelete() throws Exception {
        var s=session(user("alice"),"mine");
        mvc.perform(get("/api/auth/sessions")).andExpect(problem(401,"Unauthorized"));
        mvc.perform(delete("/api/auth/sessions/"+s.getId())).andExpect(problem(401,"Unauthorized"));
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isFalse();
    }
}
