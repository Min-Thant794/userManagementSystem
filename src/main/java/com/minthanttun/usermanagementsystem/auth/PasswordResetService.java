package com.minthanttun.usermanagementsystem.auth;

import com.minthanttun.usermanagementsystem.auth.dto.ForgotPasswordRequest;
import com.minthanttun.usermanagementsystem.auth.dto.ResetPasswordRequest;
import com.minthanttun.usermanagementsystem.common.exception.InvalidCredentialsException;
import com.minthanttun.usermanagementsystem.security.jwt.TokenHasher;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final long TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final EmailService emailService;

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            passwordResetTokenRepository.findAllByUser_IdAndUsedFalse(user.getId())
                    .forEach(token -> token.setUsed(true));

            String rawToken = generateRawToken();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHasher.hash(rawToken))
                    .expiresAt(OffsetDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        });
        // Always returns normally, whether or not the email matched - see AuthController.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = tokenHasher.hash(request.token());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset link"));

        if (resetToken.isUsed()) {
            throw new InvalidCredentialsException("This reset has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidCredentialsException("This reset link has expired");
        }

        User user = userRepository.findById(resetToken.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset link"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
