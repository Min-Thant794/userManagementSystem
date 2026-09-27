package com.minthanttun.usermanagementsystem.e2e;

import com.fasterxml.jackson.databind.*;
import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.security.ratelimit.RateLimitFilter;
import com.minthanttun.usermanagementsystem.security.session.GeoLocationService;
import com.minthanttun.usermanagementsystem.user.ProfileImageService;
import io.github.bucket4j.Bucket;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=E2eConfig.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.config.location=classpath:application-e2e.properties")
@ActiveProfiles("e2e")
@Tag("e2e")
// No test @Transactional: requests run in server threads with their own transactions.
abstract class E2eSupport {
    static final String PASSWORD="OriginalPassword123";
    private static final AtomicLong PHONE=new AtomicLong(80000000);
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment env;
    @Autowired CacheManager caches;
    @Autowired RateLimitFilter limiter;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired TokenHasher hasher;
    // Not exercised by these workflows; no real cloud uploads or external IP lookup.
    @MockitoBean ProfileImageService images;
    @MockitoBean GeoLocationService location;
    ApiClient api;
    MailpitInbox inbox;

    @BeforeEach void prepare() throws Exception {
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:25432/usermanagement_e2e");
        assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("26379");
        assertThat(env.getProperty("spring.data.redis.database")).isEqualTo("15");
        assertThat(env.getProperty("spring.mail.host")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("11025");
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("usermanagement_e2e");
        assertThat(jdbc.queryForObject("select current_user",String.class)).isEqualTo("ums_e2e");
        jdbc.execute("TRUNCATE TABLE audit_logs, oauth_accounts, refresh_tokens, " +
                "password_reset_tokens, email_verification_tokens, users RESTART IDENTITY CASCADE");
        caches.getCache("users").clear(); caches.getCache("adminUsers").clear();
        ((Map<?,?>)ReflectionTestUtils.getField(limiter,"endpointBuckets")).clear();
        ((Bucket)ReflectionTestUtils.getField(limiter,"globalBucket")).addTokens(1000);
        when(location.describeLocation(any())).thenReturn("E2E test location");
        api=new ApiClient(URI.create("http://127.0.0.1:"+port),json);
        inbox=new MailpitInbox(URI.create(env.getRequiredProperty("app.e2e.mailpit-base-url")),json);
        inbox.clear();
    }
    @AfterEach void closeClients() {
        if(api!=null) api.close();
        if(inbox!=null) inbox.close();
    }
    record Account(UUID id,String username,String email) {}
    record Tokens(String access,String refresh) {}
    Account signup(String name) throws Exception {
        String email=name+"@example.com";
        var response=api.call("POST","/api/auth/signup",Map.of("username",name,"email",email,
                "phoneNumber","+65"+PHONE.incrementAndGet(),"password",PASSWORD),null,null).expect(201);
        assertThat(response.body().path("role").asText()).isEqualTo("USER");
        assertThat(response.body().has("passwordHash")).isFalse();
        return new Account(UUID.fromString(response.body().path("id").asText()),name,email);
    }
    String verificationLink(Account account) throws Exception {
        return inbox.awaitToken(account.email(),"Verify your email address","/verify");
    }
    void verify(String token) throws Exception {
        api.call("POST","/api/auth/verify-email",Map.of("token",token),null,null).expect(204);
    }
    Account registered(String name) throws Exception {
        var account=signup(name); verify(verificationLink(account)); return account;
    }
    ApiClient.Reply loginResponse(String identifier,String password) throws Exception {
        return api.call("POST","/api/auth/login",Map.of("identifier",identifier,"password",password),null,null);
    }
    Tokens login(Account account) throws Exception { return tokens(loginResponse(account.email(),PASSWORD).expect(200)); }
    Tokens tokens(ApiClient.Reply reply) {
        assertThat(reply.body().path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(reply.body().path("accessToken").asText()).isNotBlank();
        assertThat(reply.body().has("refreshToken")).isFalse();
        assertThat(reply.cookieHeader()).contains("HttpOnly","SameSite=Lax","Path=/api/auth");
        assertThat(reply.refreshCookie()).isNotBlank();
        return new Tokens(reply.body().path("accessToken").asText(),reply.refreshCookie());
    }
    ApiClient.Reply get(String path,Tokens tokens) throws Exception {
        return api.call("GET",path,null,tokens.access(),path.startsWith("/api/auth")?tokens.refresh():null);
    }
    ApiClient.Reply refresh(String raw) throws Exception {
        return api.call("POST","/api/auth/refresh",null,null,raw);
    }
    boolean revoked(String raw) {
        return refreshTokens.findByTokenHash(hasher.hash(raw)).orElseThrow().isRevoked();
    }
    Tokens bootstrapAdmin() throws Exception {
        var admin=registered("bootstrapadmin");
        // The only account-setup bypass: establish one trusted admin before testing admin API changes.
        jdbc.update("update users set role='ADMIN' where id=?",admin.id());
        return login(admin);
    }
    JsonNode jwtPayload(String jwt) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }
}