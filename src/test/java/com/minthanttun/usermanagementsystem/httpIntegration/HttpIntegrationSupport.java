package com.minthanttun.usermanagementsystem.httpIntegration;

import com.fasterxml.jackson.databind.*;
import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.security.ratelimit.RateLimitFilter;
import com.minthanttun.usermanagementsystem.security.session.GeoLocationService;
import com.minthanttun.usermanagementsystem.user.*;
import io.github.bucket4j.Bucket;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = HttpIntegrationConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.config.location=classpath:application-http-integration.properties")
@ActiveProfiles("http-integration")
@AutoConfigureMockMvc(addFilters = true)
@Tag("http-integration")
public abstract class HttpIntegrationSupport {
    protected static final String PASSWORD = "CorrectPassword123";
    private static final AtomicLong IDS=new AtomicLong(10000000);
    private static String fixturePasswordHash;
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected RefreshTokenRepository refreshTokens;
    @Autowired protected PasswordResetTokenRepository resetTokens;
    @Autowired protected EmailVerificationTokenRepository verificationTokens;
    @Autowired protected JwtService jwt;
    @Autowired protected TokenHasher hasher;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected CacheManager caches;
    @Autowired protected Environment env;
    @Autowired protected RateLimitFilter rateLimit;
    @MockitoBean protected JavaMailSender mail;
    @MockitoBean protected ProfileImageService images;
    @MockitoBean protected GeoLocationService location;

    @BeforeEach void resetDedicatedStoresAndRateCounters() {
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:15432/usermanagement_it");
        assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("16379");
        assertThat(env.getProperty("spring.data.redis.database")).isEqualTo("15");
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("usermanagement_it");
        assertThat(jdbc.queryForObject("select current_user",String.class)).isEqualTo("ums_it");
        jdbc.execute("TRUNCATE TABLE audit_logs, oauth_accounts, refresh_tokens, " +
                "password_reset_tokens, email_verification_tokens, users RESTART IDENTITY CASCADE");
        caches.getCache("users").clear(); caches.getCache("adminUsers").clear();
        // Production limiter has no reset API. Reset only its stored test state between cases.
        ((Map<?,?>)ReflectionTestUtils.getField(rateLimit,"endpointBuckets")).clear();
        ((Bucket)ReflectionTestUtils.getField(rateLimit,"globalBucket")).addTokens(1000);
        when(location.describeLocation(any())).thenReturn("Test location");
        if(fixturePasswordHash==null) fixturePasswordHash=encoder.encode(PASSWORD);
    }

    protected User user(String name) { return user(name,Role.USER); }
    protected User user(String name,Role role) {
        return users.saveAndFlush(User.builder().username(name).email(name+"@example.com")
                .phoneNumber("+65"+IDS.incrementAndGet()).passwordHash(fixturePasswordHash)
                .role(role).emailVerified(true).build());
    }

    protected User incomplete() {
        return users.saveAndFlush(User.builder().email("oauth"+IDS.incrementAndGet()+"@example.com")
                .emailVerified(true).build());
    }

    protected MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request,User user) {
        return request.header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.generateAccessToken(user));
    }

    protected MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value));
    }

    protected JsonNode payload(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    protected Cookie refreshCookie(MvcResult result) {
        String header=result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith("refreshToken=")).findFirst().orElseThrow();
        String first=header.split(";",2)[0];
        return new Cookie("refreshToken",first.substring("refreshToken=".length()));
    }

    protected RefreshToken session(User user,String raw) {
        return session(user,raw,UUID.randomUUID(),false);
    }

    protected RefreshToken session(User user,String raw,UUID family,boolean revoked) {
        return refreshTokens.saveAndFlush(RefreshToken.builder().user(user).tokenHash(hasher.hash(raw))
                .familyId(family).revoked(revoked).expiresAt(OffsetDateTime.now().plusDays(1))
                .lastUsedAt(OffsetDateTime.now()).ipAddress("127.0.0.1").userAgent("Mozilla/5.0").build());
    }

    protected String signedToken(User user,String type,Instant expiry,String secret) {
        return Jwts.builder().subject(user.getId().toString()).claim("type",type)
                .claim("username",user.getUsername()).claim("role",user.getRole().name())
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(Instant.now().minusSeconds(600)))
                .expiration(Date.from(expiry)).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    protected ResultMatcher problem(int code,String title) {
        return result -> {
            status().is(code).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(code).match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.detail").isNotEmpty().match(result);
            jsonPath("$.stackTrace").doesNotExist().match(result);
            jsonPath("$.exception").doesNotExist().match(result);
        };
    }
}
