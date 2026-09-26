package com.minthanttun.usermanagementsystem.security.jwt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class CookieUtilTest {
    private CookieUtil util(boolean secure) {
        var util = new CookieUtil();
        ReflectionTestUtils.setField(util, "secure", secure);
        return util;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshCookieHasExpectedSecurityFlagsAndScope(boolean secure) {
        var response = new MockHttpServletResponse();
        util(secure).setRefreshTokenCookie(response, "raw-refresh", 604_800_000);
        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("refreshToken=raw-refresh;");
        List<String> attributes = Arrays.asList(header.split(";\\s*"));
        assertThat(attributes).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth", "Max-Age=604800");
        assertThat(attributes.contains("Secure")).isEqualTo(secure);
    }

    @ParameterizedTest
    @CsvSource({"1999,1", "999,0", "0,0"})
    void millisecondsAreTruncatedToWholeSeconds(long milliseconds, long seconds) {
        var response = new MockHttpServletResponse();
        util(false).setRefreshTokenCookie(response, "raw", milliseconds);
        assertThat(Arrays.asList(response.getHeader("Set-Cookie").split(";\\s*"))).contains("Max-Age=" + seconds);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clearingExpiresCookieUsingSameScopeAndFlags(boolean secure) {
        var response = new MockHttpServletResponse();
        util(secure).clearRefreshTokenCookie(response);
        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("refreshToken=;");
        List<String> attributes = Arrays.asList(header.split(";\\s*"));
        assertThat(attributes).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth", "Max-Age=0");
        assertThat(attributes.contains("Secure")).isEqualTo(secure);
    }

    @Test
    void settingRefreshCookiePreservesOtherSetCookieHeaders() {
        var response = new MockHttpServletResponse();
        response.addHeader("Set-Cookie", "other=value; Path=/");
        util(false).setRefreshTokenCookie(response, "raw", 1000);
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2).contains("other=value; Path=/");
    }
}
