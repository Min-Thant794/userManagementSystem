package com.minthanttun.usermanagementsystem.httpIntegration;

import com.minthanttun.usermanagementsystem.user.*;
import com.minthanttun.usermanagementsystem.auth.*;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.SimpleMailMessage;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerHttpIT extends HttpIntegrationSupport {
    private Map<String,Object> signup() {
        return new HashMap<>(Map.of("username","newuser","email","new@example.com",
                "phoneNumber","+6591234567","password",PASSWORD));
    }
    @Test void publicSignupReturns201AndCommitsUserAndVerificationToken() throws Exception {
        mvc.perform(body(post("/api/auth/signup"),signup())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var saved=users.findByUsername("newuser").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(encoder.matches(PASSWORD,saved.getPasswordHash())).isTrue();
        assertThat(verificationTokens.count()).isEqualTo(1);
        verify(mail).send(any(SimpleMailMessage.class));
    }
    @ParameterizedTest @ValueSource(strings={"username","email","phoneNumber","password"})
    void signupValidationRejectsInvalidFieldsWithoutWrites(String field) throws Exception {
        var data=signup(); data.put(field,field.equals("email")?"invalid":"!");
        mvc.perform(body(post("/api/auth/signup"),data)).andExpect(problem(400,"Validation Failed"))
                .andExpect(jsonPath("$.errors."+field).isNotEmpty());
        assertThat(users.count()).isZero(); assertThat(verificationTokens.count()).isZero();
        verifyNoInteractions(mail);
    }
    @Test void duplicateSignupReturns409() throws Exception {
        user("newuser");
        mvc.perform(body(post("/api/auth/signup"),signup())).andExpect(problem(409,"Duplicate Resource"));
        assertThat(users.count()).isEqualTo(1); assertThat(verificationTokens.count()).isZero();
    }
    @Test void unknownFieldsCannotSetAdminRoleDuringSignup() throws Exception {
        var data=signup(); data.put("role","ADMIN");
        mvc.perform(body(post("/api/auth/signup"),data)).andExpect(problem(400,"Malformed Request"));
        assertThat(users.count()).isZero();
    }
    @Test void malformedJsonReturnsSafeProblemResponse() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{oops"))
                .andExpect(problem(400,"Malformed Request"));
        assertThat(users.count()).isZero();
    }
    @Test void loginReturnsAccessTokenAndSecureRefreshCookie() throws Exception {
        var u=user("login");
        var result=mvc.perform(body(post("/api/auth/login"),Map.of("identifier",u.getEmail(),"password",PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        String access=payload(result).get("accessToken").asText();
        assertThat(jwt.extractUserId(access)).isEqualTo(u.getId());
        assertThat(jwt.extractTokenType(access)).isEqualTo("access");
        String cookie=result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("HttpOnly","Secure","SameSite=Lax","Path=/api/auth","Max-Age=604800");
        assertThat(refreshTokens.findByTokenHash(hasher.hash(refreshCookie(result).getValue()))).isPresent();
        // A real login-issued access token passes through the filter on the next request.
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(u.getId().toString()));
    }
    @Test @Tag("http-known-defect")
    void loginExpiresInDescribesAccessTokenLifetime() throws Exception {
        user("login");
        mvc.perform(body(post("/api/auth/login"),Map.of("identifier","login","password",PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(900));
    }
    @ParameterizedTest @ValueSource(strings={"unknown","wrong-password"})
    void invalidCredentialsReturn401AndIssueNoSession(String kind) throws Exception {
        if(kind.equals("wrong-password")) user("login");
        mvc.perform(body(post("/api/auth/login"),Map.of("identifier","login","password","WrongPassword123")))
                .andExpect(problem(401,"Authentication Failed"));
        assertThat(refreshTokens.count()).isZero();
        if(kind.equals("wrong-password"))
            assertThat(users.findByUsername("login").orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
    }
    @Test void unverifiedLoginReturns403WithResendAction() throws Exception {
        var u=user("unverified"); u.setEmailVerified(false); users.saveAndFlush(u);
        mvc.perform(body(post("/api/auth/login"),Map.of("identifier",u.getUsername(),"password",PASSWORD)))
                .andExpect(problem(403,"Email Not Verified"))
                .andExpect(jsonPath("$.action").value("resend_verification"));
        assertThat(refreshTokens.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"suspended","locked"})
    void blockedAccountsCannotLogin(String kind) throws Exception {
        var u=user("blocked");
        if(kind.equals("locked")) u.setLockedUntil(OffsetDateTime.now().plusMinutes(5));
        else u.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(u);
        mvc.perform(body(post("/api/auth/login"),Map.of("identifier",u.getUsername(),"password",PASSWORD)))
                .andExpect(problem(403,"Account Suspended"));
        assertThat(refreshTokens.count()).isZero();
    }
    @Test void refreshRotatesStoredTokenAndSetsReplacementCookie() throws Exception {
        var old=session(user("rotate"),"seed-token");
        var result=mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken","seed-token")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        assertThat(refreshTokens.findById(old.getId()).orElseThrow().isRevoked()).isTrue();
        var next=refreshTokens.findByTokenHash(hasher.hash(refreshCookie(result).getValue())).orElseThrow();
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId()); assertThat(next.isRevoked()).isFalse();
    }
    @Test void missingRefreshCookieCurrentlyReturns400() throws Exception {
        // @CookieValue is required. MVC rejects missing cookies before the controller runs.
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isBadRequest());
        assertThat(refreshTokens.count()).isZero();
    }
    @Test void unknownRefreshCookieReturns401() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken","unknown")))
                .andExpect(problem(401,"Authentication Failed"));
    }
    @Test @Tag("http-known-defect")
    void reuseReturns401AndFamilyRevocationCommits() throws Exception {
        var u=user("reuse"); var family=UUID.randomUUID(); session(u,"used",family,true);
        var child=session(u,"child",family,false); var other=session(u,"other");
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken","used")))
                .andExpect(problem(401,"Session Compromised"));
        assertThat(refreshTokens.findById(child.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findById(other.getId()).orElseThrow().isRevoked()).isFalse();
    }
    @Test void logoutRevokesSessionAndClearsCookie() throws Exception {
        var s=session(user("logout"),"raw");
        var result=mvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken","raw")))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refreshToken=;","Max-Age=0","HttpOnly","Secure","SameSite=Lax","Path=/api/auth");
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isTrue();
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }
    @Test void passwordResetRequestDoesNotRevealWhetherEmailExists() throws Exception {
        var u=user("known");
        for(String email:List.of(u.getEmail(),"unknown@example.com"))
            mvc.perform(body(post("/api/auth/forgot-password"),Map.of("email",email)))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(resetTokens.count()).isEqualTo(1);
        verify(mail,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void verificationEndpointConsumesTokenAndUpdatesUser() throws Exception {
        var u=user("verify"); u.setEmailVerified(false); users.saveAndFlush(u);
        var token=verificationTokens.saveAndFlush(EmailVerificationToken.builder().user(u)
                .tokenHash(hasher.hash("verification")).expiresAt(OffsetDateTime.now().plusHours(1)).build());
        mvc.perform(body(post("/api/auth/verify-email"),Map.of("token","verification")))
                .andExpect(status().isNoContent());
        assertThat(users.findById(u.getId()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(verificationTokens.findById(token.getId()).orElseThrow().isUsed()).isTrue();
    }
    @Test void resetEndpointChangesPasswordAndRevokesSession() throws Exception {
        var u=user("reset"); var session=session(u,"session");
        var token=resetTokens.saveAndFlush(PasswordResetToken.builder().user(u).tokenHash(hasher.hash("reset"))
                .expiresAt(OffsetDateTime.now().plusMinutes(30)).build());
        mvc.perform(body(post("/api/auth/reset-password"),Map.of("token","reset","newPassword","ChangedPassword123")))
                .andExpect(status().isNoContent());
        assertThat(encoder.matches("ChangedPassword123",users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(resetTokens.findById(token.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(refreshTokens.findById(session.getId()).orElseThrow().isRevoked()).isTrue();
    }
}