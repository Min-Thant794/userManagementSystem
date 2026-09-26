package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.user.*;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.config.location=classpath:application-integration.properties")
@Tag("integration")
// Deliberately NO @Transactional: assertions must see committed service transactions.
public abstract class IntegrationSupport {
    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    protected EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    protected TokenHasher tokenHasher;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected CacheManager cacheManager;

    @Autowired
    protected org.springframework.core.env.Environment environment;

    @Autowired
    protected PlatformTransactionManager platformTransactionManager;

    @MockitoBean
    protected JavaMailSender javaMailSender;

    @MockitoBean
    protected ProfileImageService profileImageService;

    @BeforeEach
    void cleanDedicatedStores() {
        // Fail before destructive statements if environment overrides point at other stores.
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:15432/usermanagement_it");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("16379");
        assertThat(environment.getProperty("spring.data.redis.database")).isEqualTo("15");
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
                .isEqualTo("usermanagement_it");
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo("ums_it");
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, oauth_accounts, refresh_tokens, " +
                "password_reset_tokens, email_verification_tokens, users RESTART IDENTITY CASCADE");
        cacheManager.getCache("users").clear();
        cacheManager.getCache("adminUsers").clear();
    }

    protected TransactionTemplate tx() { return new TransactionTemplate(platformTransactionManager); }

    protected User user(String name) {
        return userRepository.saveAndFlush(User.builder().username(name).email(name + "@example.com")
                .phoneNumber("+65" + String.format("%010d", Integer.toUnsignedLong(name.hashCode())))
                .passwordHash("unused-test-hash").emailVerified(true).build());
    }

    protected RefreshToken refresh(User user, String raw, UUID family, boolean revoked,
                                   OffsetDateTime expiry) {
        return refreshTokenRepository.saveAndFlush(RefreshToken.builder().user(user)
                .tokenHash(tokenHasher.hash(raw)).familyId(family).revoked(revoked)
                .expiresAt(expiry).lastUsedAt(OffsetDateTime.now()).build());
    }

    protected PasswordResetToken reset(User user, String raw, boolean used, OffsetDateTime expiry) {
        return passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder().user(user)
                .tokenHash(tokenHasher.hash(raw)).used(used).expiresAt(expiry).build());
    }

    protected EmailVerificationToken verification(User user, String raw, boolean used,
                                                  OffsetDateTime expiry) {
        return emailVerificationTokenRepository.saveAndFlush(EmailVerificationToken.builder().user(user)
                .tokenHash(tokenHasher.hash(raw)).used(used).expiresAt(expiry).build());
    }

    protected OffsetDateTime future() {
        return OffsetDateTime.now().plusDays(1);
    }
}