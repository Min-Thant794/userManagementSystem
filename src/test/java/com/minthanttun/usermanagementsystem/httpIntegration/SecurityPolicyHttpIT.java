package com.minthanttun.usermanagementsystem.httpIntegration;

import com.minthanttun.usermanagementsystem.security.jwt.JwtAuthFilter;
import com.minthanttun.usermanagementsystem.security.ratelimit.RateLimitFilter;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityPolicyHttpIT extends HttpIntegrationSupport {
    @Autowired FilterChainProxy chain;
    @Test void productionSecurityChainIncludesJwtBeforeRateLimiting() {
        var filters=chain.getFilters("/api/users/me");
        int jwtIndex=-1; int rateIndex=-1; int jwtCount=0; int rateCount=0;
        for(int i=0;i<filters.size();i++) {
            if(filters.get(i) instanceof JwtAuthFilter) { jwtIndex=i; jwtCount++; }
            if(filters.get(i) instanceof RateLimitFilter) { rateIndex=i; rateCount++; }
        }
        assertThat(jwtCount).isEqualTo(1); assertThat(rateCount).isEqualTo(1);
        assertThat(rateIndex).isGreaterThan(jwtIndex);
    }
    @Test void allowedPreflightDoesNotRequireBearerToken() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin","https://frontend.example")
                        .header("Access-Control-Request-Method","PATCH")
                        .header("Access-Control-Request-Headers","authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin","https://frontend.example"))
                .andExpect(header().string("Access-Control-Allow-Credentials","true"));
    }
    @Test void untrustedOriginIsRejectedEvenWithValidBearerToken() throws Exception {
        mvc.perform(bearer(get("/api/users/me").header("Origin","https://untrusted.example"),user("alice")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void actualAllowedOriginReceivesCorsHeadersAndSecurityHeaders() throws Exception {
        mvc.perform(bearer(get("/api/users/me").secure(true).header("Origin","https://frontend.example"),user("alice")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin","https://frontend.example"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("X-Frame-Options","DENY"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }
    @Test @Timeout(20)
    void sixthLoginRequestFromSameIpIsRateLimitedAndAnotherIpIsSeparate() throws Exception {
        // Invalid JSON fields avoid password work and lockout: this isolates the HTTP limiter.
        for(int i=0;i<5;i++)
            mvc.perform(body(post("/api/auth/login"),Map.of()))
                    .andExpect(problem(400,"Validation Failed"));
        mvc.perform(body(post("/api/auth/login"),Map.of()))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429));
        mvc.perform(body(post("/api/auth/login").with(request -> {
            request.setRemoteAddr("192.0.2.2"); return request;
        }),Map.of())).andExpect(problem(400,"Validation Failed"));
        assertThat(refreshTokens.count()).isZero();
    }
}