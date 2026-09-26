package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.security.jwt.TokenIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class TokenUniquenessIT extends IntegrationSupport {
    @Autowired
    TokenIssuer tokenIssuer;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Tag("known-defect")
    void independentlyIssuedSessionsHaveUniqueTokenIdsAndHashes() throws Exception {
        var u=user("unique");
        var first=tokenIssuer.issueNewSession(u,new MockHttpServletRequest());
        var second=tokenIssuer.issueNewSession(u,new MockHttpServletRequest());
        String one=id(first.refreshToken()); String two=id(second.refreshToken());
        // Explicit jti assertions avoid a flaky test depending on crossing a wall-clock second.

        assertThat(one).as("each refresh JWT requires a unique jti").isNotBlank();
        assertThat(two).isNotBlank().isNotEqualTo(one);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        var rows=refreshTokenRepository.findAll();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(t -> t.getTokenHash()).doesNotHaveDuplicates();
        assertThat(rows).extracting(t -> t.getFamilyId()).doesNotHaveDuplicates();
    }

    private String id(String jwt) throws Exception {
        String payload=new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),StandardCharsets.UTF_8);
        return objectMapper.readTree(payload).path("jti").asText();
    }
}
