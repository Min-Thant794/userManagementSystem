package com.minthanttun.usermanagementsystem.e2e;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@Tag("e2e-prerequisite")
class E2ePrerequisitesIT extends E2eSupport {
    @Test void separateLoginsHaveUniqueRefreshIdentity() throws Exception {
        var account=registered("identity"); var first=login(account); var second=login(account);
        String one=jwtPayload(first.refresh()).path("jti").asText();
        String two=jwtPayload(second.refresh()).path("jti").asText();
        assertThat(one).as("Add a unique jti in JwtService.buildToken before relying on rotation workflows").isNotBlank();
        assertThat(two).isNotBlank().isNotEqualTo(one);
        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        assertThat(refreshTokens.count()).isEqualTo(2);
    }
    @Test void loginAndRefreshReportTheActualAccessLifetime() throws Exception {
        var account=registered("expiry");
        var login=loginResponse(account.email(),PASSWORD).expect(200);
        assertThat(login.body().path("expiresIn").asLong()).isEqualTo(900);
        var access=jwtPayload(login.body().path("accessToken").asText());
        assertThat(access.path("exp").asLong()-access.path("iat").asLong()).isEqualTo(900);
        var refreshed=refresh(login.refreshCookie()).expect(200);
        assertThat(refreshed.body().path("expiresIn").asLong()).isEqualTo(900);
    }
}