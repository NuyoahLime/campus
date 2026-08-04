package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMailServiceTest {

    private final FakeMailDeliveryPort mail = new FakeMailDeliveryPort();
    private final AppMailProperties properties = new AppMailProperties(
            "no-reply@example.com",
            "https://app.example.com/");
    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void verificationMailContainsFrontendVerificationPath() {
        new VerificationMailService(mail, normalizer, properties)
                .sendVerificationMail("User@Example.com", "raw-token");

        assertThat(mail.single().textBody())
                .contains("https://app.example.com/verify-email?token=raw-token");
    }

    @Test
    void passwordResetMailContainsFrontendResetPath() {
        new PasswordResetMailService(mail, normalizer, properties)
                .sendPasswordResetMail("User@Example.com", "reset-token");

        assertThat(mail.single().textBody())
                .contains("https://app.example.com/reset-password?token=reset-token");
    }

    @Test
    void mailContainsRawTokenOnlyInLink() {
        new VerificationMailService(mail, normalizer, properties)
                .sendVerificationMail("user@example.com", "token-value");

        assertThat(mail.single().textBody()).containsOnlyOnce("token-value");
        assertThat(mail.single().textBody())
                .contains("/verify-email?token=token-value");
    }

    @Test
    void mailNeverContainsPassword() {
        new PasswordResetMailService(mail, normalizer, properties)
                .sendPasswordResetMail("user@example.com", "reset-token");

        assertThat(mail.single().textBody().toLowerCase())
                .doesNotContain("password=");
    }

    @Test
    void mailRecipientIsNormalizedEmail() {
        new VerificationMailService(mail, normalizer, properties)
                .sendVerificationMail("  User@Example.com  ", "raw-token");

        assertThat(mail.single().recipient()).isEqualTo("user@example.com");
    }

    private static final class FakeMailDeliveryPort implements MailDeliveryPort {
        private final List<MailMessage> messages = new ArrayList<>();

        @Override
        public void send(MailMessage message) {
            messages.add(message);
        }

        MailMessage single() {
            assertThat(messages).hasSize(1);
            return messages.getFirst();
        }
    }
}
