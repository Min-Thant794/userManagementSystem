package com.minthanttun.usermanagementsystem.httpIntegration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties="app.cookie.secure=false")
class DevelopmentCookieHttpIT extends HttpIntegrationSupport {
    @Test void developmentCookieCanDisableSecureWhileKeepingOtherAttributes() throws Exception {
        var result=mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly","SameSite=Lax","Path=/api/auth","Max-Age=0").doesNotContain("Secure");
    }
}