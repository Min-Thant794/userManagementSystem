package com.minthanttun.usermanagementsystem.security.jwt;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
class JwtAccessDeniedHandlerTest {
    @Test void writesForbiddenProblemWithoutInternalDetails() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        var response = new MockHttpServletResponse();
        new JwtAccessDeniedHandler(mapper).handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("internal authorization details"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.path("status").asInt()).isEqualTo(403);
        assertThat(body.path("title").asText()).isEqualTo("Forbidden");
        assertThat(body.path("detail").asText()).isEqualTo("You do not have permission to access this resource");
        assertThat(body.path("type").asText()).isEqualTo("about:blank");
        assertThat(response.getContentAsString()).doesNotContain("internal authorization details");
    }
}
