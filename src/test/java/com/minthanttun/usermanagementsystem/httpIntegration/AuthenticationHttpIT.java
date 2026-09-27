package com.minthanttun.usermanagementsystem.httpIntegration;

import com.minthanttun.usermanagementsystem.user.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationHttpIT extends HttpIntegrationSupport {
    @Test void missingAuthorizationIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(problem(401,"Unauthorized"));
    }
    @ParameterizedTest @ValueSource(strings={"Basic dXNlcjpwYXNz", "Bearer ", "Bearer nonsense", "Bearer a.b.c"})
    void badAuthorizationDoesNotAuthenticate(String header) throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization",header))
                .andExpect(problem(401,"Unauthorized"));
    }
    @ParameterizedTest @ValueSource(strings={"expired","wrong-signature","refresh"})
    void validJwtStructureDoesNotBypassTokenChecks(String kind) throws Exception {
        var u=user("tokenuser");
        String secret=kind.equals("wrong-signature")?
                "different-key-0123456789-0123456789-0123456789":env.getProperty("app.jwt.secret");
        String token=signedToken(u,kind.equals("refresh")?"refresh":"access",
                Instant.now().plusSeconds(kind.equals("expired")?-60:600),secret);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token))
                .andExpect(problem(401,"Unauthorized"));
    }
    @Test void genuineAccessTokenResolvesDatabasePrincipal() throws Exception {
        var u=user("alice");
        mvc.perform(bearer(get("/api/users/me"),u)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.getId().toString()))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void authenticationDoesNotLeakToFollowingRequest() throws Exception {
        mvc.perform(bearer(get("/api/users/me"),user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(problem(401,"Unauthorized"));
    }
    @ParameterizedTest @ValueSource(strings={"suspended","locked"})
    void databaseStatusIsRecheckedAfterTokenIssuance(String kind) throws Exception {
        var u=user("status"); String token=jwt.generateAccessToken(u);
        if(kind.equals("suspended")) u.setStatus(AccountStatus.SUSPENDED);
        else u.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        users.saveAndFlush(u);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token))
                .andExpect(problem(403,"Account Suspended"));
    }
    @Test void incompleteProfileGetsActionable403() throws Exception {
        mvc.perform(bearer(get("/api/users/me"),incomplete()))
                .andExpect(problem(403,"Profile Incomplete"))
                .andExpect(jsonPath("$.action").value("complete_profile"));
    }
    @Test @Tag("http-known-defect")
    void tokenForDeletedUserReturns401InsteadOfEscapingFilter() throws Exception {
        var u=user("deleted"); String token=jwt.generateAccessToken(u); users.deleteById(u.getId());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token))
                .andExpect(problem(401,"Unauthorized"));
    }
}