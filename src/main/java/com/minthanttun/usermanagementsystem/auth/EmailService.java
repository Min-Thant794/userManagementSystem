package com.minthanttun.usermanagementsystem.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.reset-password-base-url}")
    private String resetPasswordBaseUrl;

    @Value("${app.mail.verify-email-base-url}")
    private String verifyEmailBaseUrl;

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String resetLink = resetPasswordBaseUrl + "?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        message.setText(
                "We received a request to reset your password.\n\n" +
                        "Click the link below to choose a new password. This link expires in 30 minutes:\n\n" +
                        resetLink + "\n\n" +
                        "If you didn't request this, you can safely ignore this email."
        );

        mailSender.send(message);
    }

    public void sendVerificationEmail(String toEmail, String rawToken) {
        String verifyLink = verifyEmailBaseUrl + "?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Verify your email address");
        message.setText(
                "Thanks for signing up!\n\n" +
                        "Please confirm your email address by clicking the link below. This link expires in 24 hours:\n\n" +
                        verifyLink + "\n\n" +
                        "If you didn't create this account, you can safely ignore this email."
        );

        mailSender.send(message);
    }
}
