package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.auth.dto.*;
import com.minthanttun.usermanagementsystem.admin.AdminUserService;
import com.minthanttun.usermanagementsystem.admin.dto.AdminUpdateUserRequest;
import com.minthanttun.usermanagementsystem.user.UserService;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountTransactionIT extends IntegrationSupport {
    @Autowired
    AuthService auth;

    @Autowired
    PasswordResetService passwords;

    @Autowired
    EmailVerificationService emails;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    UserService profiles;

    @Autowired
    AdminUserService admin;

    @Test
    void signupCommitsUserHashedPasswordAndVerificationToken() {
        var saved=auth.signup(new SignupRequest("signup","signup@example.com","+6590000000","Password123"));
        var stored=userRepository.findById(saved.getId()).orElseThrow();

        assertThat(encoder.matches("Password123",stored.getPasswordHash())).isTrue();
        assertThat(stored.isEmailVerified()).isFalse();

        var mail=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mail.capture());
        String raw=mail.getValue().getText().split("token=")[1].split("\\s")[0];

        assertThat(emailVerificationTokenRepository.findByTokenHash(tokenHasher.hash(raw))).isPresent();
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void signupRollsBackUserAndVerificationTokenWhenMailFails() {
        doThrow(new MailSendException("offline")).when(javaMailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> auth.signup(new SignupRequest("signup","signup@example.com","+6590000000","Password123")))
                .isInstanceOf(MailSendException.class);
        assertThat(userRepository.count()).isZero(); assertThat(emailVerificationTokenRepository.count()).isZero();
    }

    @Test
    void forgotPasswordCommitsDirtyCheckedInvalidationOfOldToken() {
        var u=user("forgot"); var old=reset(u,"old",false,future());
        passwords.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        assertThat(passwordResetTokenRepository.findById(old.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(passwordResetTokenRepository.findAllByUser_IdAndUsedFalse(u.getId())).hasSize(1);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void mailFailureRollsBackOldTokenInvalidationAndNewToken() {
        var u=user("forgot"); var old=reset(u,"old",false,future());
        doThrow(new MailSendException("offline")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> passwords.forgotPassword(new ForgotPasswordRequest(u.getEmail())))
                .isInstanceOf(MailSendException.class);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);
        assertThat(passwordResetTokenRepository.findById(old.getId()).orElseThrow().isUsed()).isFalse();
    }

    @Test
    void passwordResetCommitsPasswordTokenConsumptionAndSessionRevocationTogether() {
        var u=user("reset"); var other=user("other"); var token=reset(u,"raw",false,future());
        var mine=refresh(u,"mine",UUID.randomUUID(),false,future());
        var theirs=refresh(other,"theirs",UUID.randomUUID(),false,future());
        passwords.resetPassword(new ResetPasswordRequest("raw","NewPassword123"));

        assertThat(encoder.matches("NewPassword123",userRepository.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(passwordResetTokenRepository.findById(token.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(refreshTokenRepository.findById(mine.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(theirs.getId()).orElseThrow().isRevoked()).isFalse();
        assertThatThrownBy(() -> passwords.resetPassword(new ResetPasswordRequest("raw","Another123")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(encoder.matches("NewPassword123",userRepository.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void expiredResetLeavesAllStoredStateUntouched() {
        var u=user("expired"); var t=reset(u,"raw",false,java.time.OffsetDateTime.now().minusDays(1));
        var session=refresh(u,"session",UUID.randomUUID(),false,future());

        assertThatThrownBy(() -> passwords.resetPassword(new ResetPasswordRequest("raw","Password123")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getPasswordHash()).isEqualTo(u.getPasswordHash());
        assertThat(passwordResetTokenRepository.findById(t.getId()).orElseThrow().isUsed()).isFalse();
        assertThat(refreshTokenRepository.findById(session.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void pendingEmailVerificationCommitsChangeRevokesSessionsAndEvictsCaches() {
        var u=user("pending"); u.setPendingEmail("new@example.com");
        userRepository.saveAndFlush(u);

        var t=verification(u,"raw",false,future());
        var session=refresh(u,"session",UUID.randomUUID(),false,future());
        profiles.getCachedProfile(u.getId()); admin.getCachedUserResponse(u.getId());
        emails.verifyEmail("raw");
        var stored=userRepository.findById(u.getId()).orElseThrow();

        assertThat(stored.getEmail()).isEqualTo("new@example.com");
        assertThat(stored.getPendingEmail()).isNull(); assertThat(stored.isEmailVerified()).isTrue();
        assertThat(emailVerificationTokenRepository.findById(t.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(refreshTokenRepository.findById(session.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(cacheManager.getCache("users").get(u.getId())).isNull();
        assertThat(cacheManager.getCache("adminUsers").get(u.getId())).isNull();
        assertThat(profiles.getCachedProfile(u.getId()).email()).isEqualTo("new@example.com");
    }

    @Test
    void initialVerificationDoesNotRevokeSessions() {
        var u=user("initial"); u.setEmailVerified(false);
        userRepository.saveAndFlush(u);

        var t=verification(u,"raw",false,future());
        var session=refresh(u,"session",UUID.randomUUID(),false,future());
        emails.verifyEmail("raw");

        assertThat(userRepository.findById(u.getId()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(emailVerificationTokenRepository.findById(t.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(refreshTokenRepository.findById(session.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void failedLoginCounterCommitsDespiteOuterLoginException() {
        var u=user("login"); u.setPasswordHash(encoder.encode("Correct123"));
        userRepository.saveAndFlush(u);

        for(int i=0;i<5;i++) {
            assertThatThrownBy(() -> auth.login(new LoginRequest("login","Wrong123"),new MockHttpServletRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        var stored=userRepository.findById(u.getId()).orElseThrow();

        assertThat(stored.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(stored.getLockedUntil()).isAfter(java.time.OffsetDateTime.now());
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void adminUpdateCommitsUserAndQueryableJsonAudit() {
        var target=user("target"); var actor=user("actor");
        admin.updateUser(target.getId(),new AdminUpdateUserRequest("after",null,null),actor);

        assertThat(userRepository.findById(target.getId()).orElseThrow().getUsername()).isEqualTo("after");
        assertThat(jdbcTemplate.queryForObject("select details->'after'->>'username' from audit_logs " +
                        "where actor_user_id=? and target_user_id=? and action='UPDATE'",String.class,
                actor.getId(),target.getId())).isEqualTo("after");
    }

    @Test
    void auditForeignKeyFailureRollsBackUserUpdate() {
        var target=user("target");
        var missingActor=com.minthanttun.usermanagementsystem.user.User.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> admin.updateUser(target.getId(),
                new AdminUpdateUserRequest("after",null,null),missingActor))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getUsername()).isEqualTo("target");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_logs",Integer.class)).isZero();
    }
}