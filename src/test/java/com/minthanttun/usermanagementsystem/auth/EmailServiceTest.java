package com.minthanttun.usermanagementsystem.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("remaining-unit")
public class EmailServiceTest {
    @Mock JavaMailSender javaMailSender;
    @InjectMocks EmailService emailService;
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                emailService,
                "resetPasswordBaseUrl",
                "https://frontend.example/reset"
        );

        ReflectionTestUtils.setField(
                emailService,
                "verifyEmailBaseUrl",
                "https://frontend.example/verify"
        );
    }
    private void send(String kind) {
        if (kind.equals("reset")) {
            emailService.sendPasswordResetEmail("test@example.com", "raw-token_123");
        } else {
            emailService.sendVerificationEmail("test@example.com", "raw-token_123");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"reset", "verify"})
    void sendsCorrectRecipientSubjectLinkAndExpiryText(String kind) {
        send(kind);
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("test@example.com");
        assertThat(mail.getValue().getSubject()).isEqualTo(kind.equals("reset") ? "Reset your password" : "Verify your email address");
        assertThat(mail.getValue().getText()).contains("https://frontend.example/" + kind + "?token=raw-token_123")
                .contains(kind.equals("reset") ? "30 minutes" : "24 hours");
        verifyNoMoreInteractions(javaMailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "verify"})
    void mailFailureIsPropagated(String kind) {
        var failure = new MailSendException("SMTP unavailable");
        doThrow(failure).when(javaMailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> send(kind)).isSameAs(failure);
    }
}
