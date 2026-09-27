package com.minthanttun.usermanagementsystem.e2e;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdminWorkflowIT extends E2eSupport {
    @Test void promotionThenDemotionChangesAccessWithoutIssuingAnotherAccessToken() throws Exception {
        var admin=bootstrapAdmin(); var target=registered("target"); var session=login(target);
        get("/api/admin/users",session).expect(403);
        api.call("PATCH","/api/admin/users/"+target.id()+"/role",Map.of("role","ADMIN"),admin.access(),null).expect(200);
        get("/api/admin/users",session).expect(200);
        api.call("PATCH","/api/admin/users/"+target.id()+"/role",Map.of("role","USER"),admin.access(),null).expect(200);
        get("/api/admin/users",session).expect(403);
        var audits=get("/api/admin/audit-logs?targetUserId="+target.id()+"&action=ROLE_CHANGE",admin).expect(200).body();
        assertThat(audits.path("content").size()).isEqualTo(2);
    }
    @Test void suspensionBlocksExistingAccessAndReactivationRestoresIt() throws Exception {
        var admin=bootstrapAdmin(); var target=registered("suspendtarget"); var session=login(target);
        get("/api/users/me",session).expect(200);
        api.call("PATCH","/api/admin/users/"+target.id()+"/status",Map.of("status","SUSPENDED"),admin.access(),null).expect(200);
        get("/api/users/me",session).expect(403);
        loginResponse(target.email(),PASSWORD).expect(403);
        api.call("PATCH","/api/admin/users/"+target.id()+"/status",Map.of("status","ACTIVE"),admin.access(),null).expect(200);
        get("/api/users/me",session).expect(200);
        var audits=get("/api/admin/audit-logs?targetUserId="+target.id()+"&sort=id,asc",admin).expect(200).body().path("content");
        assertThat(audits.size()).isEqualTo(2);
        assertThat(audits.get(0).path("action").asText()).isEqualTo("SUSPEND");
        assertThat(audits.get(1).path("action").asText()).isEqualTo("REACTIVATE");
    }
}