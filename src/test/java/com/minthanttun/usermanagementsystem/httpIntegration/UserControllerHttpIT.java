package com.minthanttun.usermanagementsystem.httpIntegration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerHttpIT extends HttpIntegrationSupport {
    @Test void profileUpdateChangesOnlyAuthenticatedUserAndEvictsCache() throws Exception {
        var alice=user("alice"); var bob=user("bob");
        mvc.perform(bearer(get("/api/users/me"),alice)).andExpect(status().isOk());
        mvc.perform(bearer(body(patch("/api/users/me"),Map.of("username","updated")),alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("updated"));
        mvc.perform(bearer(get("/api/users/me"),alice)).andExpect(jsonPath("$.username").value("updated"));
        assertThat(users.findById(bob.getId()).orElseThrow().getUsername()).isEqualTo("bob");
    }
    @Test void invalidUpdateReturnsFieldErrorAndDoesNotWrite() throws Exception {
        var u=user("alice");
        mvc.perform(bearer(body(patch("/api/users/me"),Map.of("phoneNumber","invalid")),u))
                .andExpect(problem(400,"Validation Failed")).andExpect(jsonPath("$.errors.phoneNumber").isNotEmpty());
        assertThat(users.findById(u.getId()).orElseThrow().getPhoneNumber()).isEqualTo(u.getPhoneNumber());
    }
    @Test void updateCannotInjectRoleOrAnotherUserId() throws Exception {
        var u=user("alice"); var other=user("bob");
        mvc.perform(bearer(body(patch("/api/users/me"),Map.of("id",other.getId(),"role","ADMIN")),u))
                .andExpect(problem(400,"Malformed Request"));
        assertThat(users.findById(u.getId()).orElseThrow().getRole()).isEqualTo(u.getRole());
    }
    @Test void duplicateUsernameReturns409() throws Exception {
        var u=user("alice"); user("taken");
        mvc.perform(bearer(body(patch("/api/users/me"),Map.of("username","taken")),u))
                .andExpect(problem(409,"Duplicate Resource"));
        assertThat(users.findById(u.getId()).orElseThrow().getUsername()).isEqualTo("alice");
    }
    @Test void emailUpdateReturnsPendingEmailAndKeepsCurrentEmail() throws Exception {
        var u=user("alice");
        mvc.perform(bearer(body(patch("/api/users/me"),Map.of("email","new@example.com")),u))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(u.getEmail()))
                .andExpect(jsonPath("$.pendingEmail").value("new@example.com"));
        assertThat(verificationTokens.count()).isEqualTo(1);
    }
    @Test void passwordChangeRequiresCurrentPasswordAndRevokesSessions() throws Exception {
        var u=user("password"); var s=session(u,"session");
        mvc.perform(bearer(body(put("/api/users/me/password"),
                        Map.of("currentPassword","Wrong123","newPassword","Changed123")),u))
                .andExpect(problem(401,"Authentication Failed"));
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isFalse();
        mvc.perform(bearer(body(put("/api/users/me/password"),
                        Map.of("currentPassword",PASSWORD,"newPassword","Changed123")),u))
                .andExpect(status().isNoContent());
        assertThat(encoder.matches("Changed123",users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(refreshTokens.findById(s.getId()).orElseThrow().isRevoked()).isTrue();
    }
    @Test void completedOAuthProfileCanSetInitialPasswordOnlyOnce() throws Exception {
        var u=user("oauth"); u.setPasswordHash(null); users.saveAndFlush(u);
        mvc.perform(bearer(body(put("/api/users/me/password/initial"),Map.of("newPassword","Initial123")),u))
                .andExpect(status().isNoContent());
        mvc.perform(bearer(body(put("/api/users/me/password/initial"),Map.of("newPassword","Another123")),u))
                .andExpect(problem(401,"Authentication Failed"));
        assertThat(encoder.matches("Initial123",users.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void incompleteUserCanCompleteProfileThenReadItWithSameAccessToken() throws Exception {
        var u=incomplete(); String access=jwt.generateAccessToken(u);
        mvc.perform(body(post("/api/users/me/complete-profile"),Map.of("username","completed","phoneNumber","+6591234567"))
                .header("Authorization","Bearer "+access)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("completed"));
    }
    @Test void incompleteUserCanUploadAndDeletePhoto() throws Exception {
        var u=incomplete(); String url="https://images.example/avatar.png";
        when(images.uploadImage(any(),eq(u.getId()))).thenReturn(url);
        var file=new MockMultipartFile("file","avatar.png","image/png",new byte[]{1,2,3});
        // Image decoding is outside this HTTP test: the hosting service is a boundary mock.
        mvc.perform(bearer(multipart(HttpMethod.PUT,"/api/users/me/photo").file(file),u))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileImageUrl").value(url));
        assertThat(users.findById(u.getId()).orElseThrow().getProfileImageUrl()).isEqualTo(url);
        mvc.perform(bearer(delete("/api/users/me/photo"),u)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
        verify(images).deleteImage(u.getId());
        assertThat(users.findById(u.getId()).orElseThrow().getProfileImageUrl()).isNull();
    }
}