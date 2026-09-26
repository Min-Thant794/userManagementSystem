package com.minthanttun.usermanagementsystem.security.jwt;

import org.junit.jupiter.api.Tag;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.BadCredentialsException;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class JwtAuthenticationEntryPointTest {
    @ParameterizedTest
    @CsvSource(value = {"NULL,401,Unauthorized,Authentication is required to access this resource,NULL", "UNRECOGNIZED,401,Unauthorized,Authentication is required to access this resource,NULL",
            "ACCOUNT_SUSPENDED,403,Account Suspended,This account has been suspended,NULL",
            "PROFILE_INCOMPLETE,403,Profile Incomplete,Please complete your profile before continuing,complete_profile"},
            nullValues = "NULL")
    void writesExpectedProblemBody(String error, int status, String title, String detail, String action) throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        var handler = new JwtAuthenticationEntryPoint(mapper);
        var request = new MockHttpServletRequest();

        if (error != null) {
            request.setAttribute("auth_error", error);
        }

        var response = new MockHttpServletResponse();
        handler.commence(request, response, new BadCredentialsException("internal credential detail"));
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("title").asText()).isEqualTo(title);
        assertThat(body.path("detail").asText()).isEqualTo(detail);
        assertThat(body.path("type").asText()).isEqualTo("about:blank");

        if (action != null) {
            assertThat(body.path("action").asText()).isEqualTo(action);
        } else {
            assertThat(body.has("action")).isFalse();
        }

        assertThat(response.getContentAsString()).doesNotContain("internal credential detail");
    }
}
