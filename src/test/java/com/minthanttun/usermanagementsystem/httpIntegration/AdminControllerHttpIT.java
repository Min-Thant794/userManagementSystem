package com.minthanttun.usermanagementsystem.httpIntegration;

import com.minthanttun.usermanagementsystem.user.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AdminControllerHttpIT extends HttpIntegrationSupport {
    @Test void searchPaginationUsesProductionPageSerialization() throws Exception {
        var admin=user("administrator",Role.ADMIN); user("alice"); user("bob"); user("charlie");
        mvc.perform(bearer(get("/api/admin/users").param("role","USER").param("status","ACTIVE")
                        .param("page","1").param("size","2").param("sort","username,asc"),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("charlie"));
        mvc.perform(bearer(get("/api/admin/users").param("search","ALI"),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].username").value("alice"));
    }
    @Test void missingUserReturns404() throws Exception {
        mvc.perform(bearer(get("/api/admin/users/"+UUID.randomUUID()),user("admin",Role.ADMIN)))
                .andExpect(problem(404,"Resource Not Found"));
    }
    @Test void updatePersistsUserAndAuditThenAuditEndpointFiltersIt() throws Exception {
        var admin=user("admin",Role.ADMIN); var target=user("target");
        mvc.perform(bearer(body(put("/api/admin/users/"+target.getId()),Map.of("username","updated")),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("updated"));
        assertThat(users.findById(target.getId()).orElseThrow().getUsername()).isEqualTo("updated");
        mvc.perform(bearer(get("/api/admin/audit-logs").param("actorUserId",admin.getId().toString())
                        .param("targetUserId",target.getId().toString()).param("action","UPDATE"),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].details.before.username").value("target"))
                .andExpect(jsonPath("$.content[0].details.after.username").value("updated"));
    }
    @Test void statusAndRoleChangesArePersistedAndAudited() throws Exception {
        var admin=user("admin",Role.ADMIN); var target=user("target");
        mvc.perform(bearer(body(patch("/api/admin/users/"+target.getId()+"/status"),
                        Map.of("status","SUSPENDED")),admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(bearer(body(patch("/api/admin/users/"+target.getId()+"/role"),
                        Map.of("role","ADMIN")),admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(jdbc.queryForList("select action from audit_logs order by id",String.class))
                .containsExactly("SUSPEND","ROLE_CHANGE");
        var stored=users.findById(target.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
    }
    @ParameterizedTest @ValueSource(strings={"role","status"})
    void cannotRemoveLastActiveAdmin(String field) throws Exception {
        var admin=user("admin",Role.ADMIN);
        mvc.perform(bearer(body(patch("/api/admin/users/"+admin.getId()+"/"+field),
                        Map.of(field,field.equals("role")?"USER":"SUSPENDED")),admin))
                .andExpect(problem(409,"Cannot Remove Last Admin"));
        var stored=users.findById(admin.getId()).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs",Integer.class)).isZero();
    }
    @Test void unknownRoleValueIsRejectedBeforeMutation() throws Exception {
        var admin=user("admin",Role.ADMIN); var target=user("target");
        mvc.perform(bearer(body(patch("/api/admin/users/"+target.getId()+"/role"),Map.of("role","OWNER")),admin))
                .andExpect(problem(400,"Malformed Request"));
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }
    @Test void adminCreationHashesPasswordAndRecordsActor() throws Exception {
        var admin=user("admin",Role.ADMIN);
        mvc.perform(bearer(body(post("/api/admin/users"),Map.of("username","newadmin","email","newadmin@example.com",
                        "phoneNumber","+6591234567","password",PASSWORD)),admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var saved=users.findByUsername("newadmin").orElseThrow();
        assertThat(encoder.matches(PASSWORD,saved.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select actor_user_id from audit_logs where action='ADMIN_CREATED'",
                UUID.class)).isEqualTo(admin.getId());
    }
}
