package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

class DatabaseContractIT extends IntegrationSupport {
    @Test
    void productionMigrationsRanSuccessfully() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history " +
                "where success and version is not null", Integer.class)).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "email", "phone"})
    void databaseRejectsDuplicateIdentityFields(String field) {
        var first = user("first");
        var second = User.builder().username("second").email("second@example.com")
                .phoneNumber("+6599999999").build();
        switch (field) {
            case "username" -> second.setUsername(first.getUsername());
            case "email" -> second.setEmail(first.getEmail());
            case "phone" -> second.setPhoneNumber(first.getPhoneNumber());
        }
        // Flush/commit forces PostgreSQL, rather than a service pre-check, to reject it.
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void emailCannotBeNullButOAuthProfileFieldsCan() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
        userRepository.saveAndFlush(User.builder().email("oauth1@example.com").build());
        userRepository.saveAndFlush(User.builder().email("oauth2@example.com").build());
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void databaseRejectsUnknownRoleAndOrphanSession() {
        var u = user("constraint");
        assertThatThrownBy(() -> jdbcTemplate.update("update users set role='OWNER' where id=?", u.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("insert into refresh_tokens " +
                        "(user_id, token_hash, expires_at) values (?, 'orphan', now()+interval '1 day')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oauthProviderSubjectIsUniqueAcrossUsers() {
        var a = user("oauthA"); var b = user("oauthB");
        jdbcTemplate.update("insert into oauth_accounts(user_id,provider,provider_user_id) values (?,'google','subject')", a.getId());
        assertThatThrownBy(() -> jdbcTemplate.update("insert into oauth_accounts(user_id,provider,provider_user_id) " +
                "values (?,'google','subject')", b.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A different provider may use the same subject.
        jdbcTemplate.update("insert into oauth_accounts(user_id,provider,provider_user_id) values (?,'github','subject')", b.getId());
    }

    @Test
    void deletingUserCascadesToOwnedTokensAndOAuthLinks() {
        var u = user("cascade");
        refresh(u, "r", UUID.randomUUID(), false, future());
        reset(u, "p", false, future()); verification(u, "v", false, future());
        jdbcTemplate.update("insert into oauth_accounts(user_id,provider,provider_user_id) values (?,'google','s')", u.getId());
        userRepository.deleteById(u.getId());

        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(passwordResetTokenRepository.count()).isZero();
        assertThat(emailVerificationTokenRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from oauth_accounts", Integer.class)).isZero();
    }
}