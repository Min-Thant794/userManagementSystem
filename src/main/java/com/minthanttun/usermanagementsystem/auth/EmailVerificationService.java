package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final long TOKEN_EXPIRY_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final TokenHasher tokenHasher;
    private final EmailService emailService;

    @Transactional
    public void generateVerificationEmail(User user) {
        // Invalidate any previously issued, still-unused tokens for this user.
        emailVerificationTokenRepository.findAllByUser_IdAndUsedFalse(user.getId())
                .forEach(token -> token.setUsed(true));

        String rawToken = generateRawToken();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .expiresAt(OffsetDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                .used(false)
                .build();
        emailVerificationTokenRepository.save(token);

        emailService.sendVerificationEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = tokenHasher.hash(rawToken);

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired verification link"));

        if (token.isUsed()) {
            throw new InvalidCredentialsException("This verification link has already been used");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException("This verification link has expired");
        }

        User user = userRepository.findById(token.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired verification link"));
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                generateVerificationEmail(user);
            }
            // Already verified — silently do nothing, same non-enumeration
            // reasoning as forgotPassword().
        });
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
