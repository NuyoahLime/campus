package com.campusguinness.identity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AuthTokenPersistenceTest extends PostgreSqlIntegrationTestSupport {

    @Autowired private UserJpaRepository users;
    @Autowired private EmailVerificationTokenJpaRepository emailTokens;
    @Autowired private PasswordResetTokenJpaRepository passwordTokens;
    @Autowired private EntityManager entityManager;

    @Test
    void emailVerificationTokenCanBePersisted() {
        var user = users.saveAndFlush(user("email-token-user"));
        var token = emailToken(user.getId(), hash('a'), "PUBLIC_REGISTRATION",
                "student@example.com", Instant.now().plusSeconds(3600), null);

        var saved = emailTokens.saveAndFlush(token);

        assertThat(emailTokens.findById(saved.getId())).isPresent();
        assertThat(saved.getTokenHash()).isEqualTo(hash('a'));
    }

    @Test
    void passwordResetTokenCanBePersisted() {
        var user = users.saveAndFlush(user("password-token-user"));
        var token = passwordToken(user.getId(), hash('b'),
                Instant.now().plusSeconds(1800), null);

        var saved = passwordTokens.saveAndFlush(token);

        assertThat(passwordTokens.findById(saved.getId())).isPresent();
        assertThat(saved.getTokenHash()).isEqualTo(hash('b'));
    }

    @Test
    void duplicateTokenHashRejected() {
        var user = users.saveAndFlush(user("duplicate-token-user"));
        emailTokens.saveAndFlush(emailToken(user.getId(), hash('c'), "PUBLIC_REGISTRATION",
                "first@example.com", Instant.now().plusSeconds(3600), null));

        assertThatThrownBy(() -> emailTokens.saveAndFlush(emailToken(user.getId(), hash('c'),
                "RECOVERY_EMAIL", "second@example.com", Instant.now().plusSeconds(3600), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tokenReferencesExistingUser() {
        assertThatThrownBy(() -> passwordTokens.saveAndFlush(passwordToken(UUID.randomUUID(),
                hash('d'), Instant.now().plusSeconds(1800), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserDeletesTokens() {
        var user = users.saveAndFlush(user("cascade-token-user"));
        var email = emailTokens.saveAndFlush(emailToken(user.getId(), hash('e'), "PUBLIC_REGISTRATION",
                "cascade@example.com", Instant.now().plusSeconds(3600), null));
        var password = passwordTokens.saveAndFlush(passwordToken(user.getId(), hash('f'),
                Instant.now().plusSeconds(1800), null));

        users.delete(user);
        users.flush();
        entityManager.clear();

        assertThat(emailTokens.findById(email.getId())).isEmpty();
        assertThat(passwordTokens.findById(password.getId())).isEmpty();
    }

    @Test
    void activeTokenQueryExcludesUsedToken() {
        var user = users.saveAndFlush(user("used-token-user"));
        var now = Instant.now();
        var createdAt = now.minusSeconds(60);
        emailTokens.saveAndFlush(emailToken(user.getId(), hash('g'), "PUBLIC_REGISTRATION",
                "used@example.com", now.plusSeconds(3600), now, createdAt));
        passwordTokens.saveAndFlush(passwordToken(user.getId(), hash('h'),
                now.plusSeconds(1800), now, createdAt));

        assertThat(emailTokens.findByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), "PUBLIC_REGISTRATION", now)).isEmpty();
        assertThat(passwordTokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), now)).isEmpty();
    }

    @Test
    void activeTokenQueryExcludesExpiredToken() {
        var user = users.saveAndFlush(user("expired-token-user"));
        var now = Instant.now();
        var createdAt = now.minusSeconds(120);
        var expiredAt = now.minusSeconds(60);
        emailTokens.saveAndFlush(emailToken(user.getId(), hash('i'), "PUBLIC_REGISTRATION",
                "expired@example.com", expiredAt, null, createdAt));
        passwordTokens.saveAndFlush(passwordToken(user.getId(), hash('j'),
                expiredAt, null, createdAt));

        assertThat(emailTokens.findByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), "PUBLIC_REGISTRATION", now)).isEmpty();
        assertThat(passwordTokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), now)).isEmpty();
    }

    @Test
    void activeTokenQueryIncludesOnlyUnusedAndUnexpiredTokens() {
        var user = users.saveAndFlush(user("active-token-user"));
        var now = Instant.now();
        var email = emailTokens.saveAndFlush(emailToken(user.getId(), hash('k'), "RECOVERY_EMAIL",
                "active@example.com", now.plusSeconds(3600), null));
        var password = passwordTokens.saveAndFlush(passwordToken(user.getId(), hash('l'),
                now.plusSeconds(1800), null));

        assertThat(emailTokens.findByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), "RECOVERY_EMAIL", now))
                .extracting(EmailVerificationTokenEntity::getId)
                .containsExactly(email.getId());
        assertThat(passwordTokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), now))
                .extracting(PasswordResetTokenEntity::getId)
                .containsExactly(password.getId());
    }

    private UserEntity user(String username) {
        var now = Instant.now();
        var entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(username + "-" + UUID.randomUUID());
        entity.setPasswordHash("$2a$12$012345678901234567890u0123456789012345678901234567890123");
        entity.setAccountStatus("NORMAL");
        entity.setLoginFailures(0);
        entity.setRegistrationSource("ADMIN_PROVISIONED");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private EmailVerificationTokenEntity emailToken(UUID userId, String hash, String purpose,
            String targetEmail, Instant expiresAt, Instant usedAt) {
        return new EmailVerificationTokenEntity(UUID.randomUUID(), userId, hash, purpose,
                targetEmail, expiresAt, usedAt, Instant.now());
    }

    private EmailVerificationTokenEntity emailToken(UUID userId, String hash, String purpose,
            String targetEmail, Instant expiresAt, Instant usedAt, Instant createdAt) {
        return new EmailVerificationTokenEntity(UUID.randomUUID(), userId, hash, purpose,
                targetEmail, expiresAt, usedAt, createdAt);
    }

    private PasswordResetTokenEntity passwordToken(UUID userId, String hash,
            Instant expiresAt, Instant usedAt) {
        return new PasswordResetTokenEntity(UUID.randomUUID(), userId, hash,
                expiresAt, usedAt, Instant.now(), "127.0.0.1");
    }

    private PasswordResetTokenEntity passwordToken(UUID userId, String hash,
            Instant expiresAt, Instant usedAt, Instant createdAt) {
        return new PasswordResetTokenEntity(UUID.randomUUID(), userId, hash,
                expiresAt, usedAt, createdAt, "127.0.0.1");
    }

    private String hash(char c) {
        return String.valueOf(c).repeat(64);
    }
}
