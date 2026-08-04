package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.service.EmailVerificationInvalidException;
import com.campusguinness.identity.application.service.PendingVerificationMail;
import com.campusguinness.identity.application.service.PublicRegistrationUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicRegistrationPersistenceService {

    private static final String PURPOSE_PUBLIC_REGISTRATION = "PUBLIC_REGISTRATION";

    private final UserJpaRepository users;
    private final SchoolMembershipJpaRepository memberships;
    private final EmailVerificationTokenJpaRepository emailTokens;

    public PublicRegistrationPersistenceService(UserJpaRepository users,
            SchoolMembershipJpaRepository memberships,
            EmailVerificationTokenJpaRepository emailTokens) {
        this.users = users;
        this.memberships = memberships;
        this.emailTokens = emailTokens;
    }

    @Transactional
    public void createPublicRegistration(String username, String emailNormalized,
            String passwordHash, String tokenHash, Duration ttl, Instant now) {
        try {
            var userId = UUID.randomUUID();
            var user = new UserEntity();
            user.setId(userId);
            user.setUsername(username);
            user.setPasswordHash(passwordHash);
            user.setAccountStatus("NORMAL");
            user.setPlatformRole("REGISTERED_USER");
            user.setLockedUntil(null);
            user.setLoginFailures(0);
            user.setActivationIssuedAt(null);
            user.setActivationExpiresAt(null);
            user.setEmail(emailNormalized);
            user.setEmailNormalized(emailNormalized);
            user.setEmailVerifiedAt(null);
            user.setRegistrationSource("PUBLIC");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            users.saveAndFlush(user);

            emailTokens.saveAndFlush(new EmailVerificationTokenEntity(
                    UUID.randomUUID(),
                    userId,
                    tokenHash,
                    PURPOSE_PUBLIC_REGISTRATION,
                    emailNormalized,
                    now.plus(ttl),
                    null,
                    now));
        } catch (DataIntegrityViolationException e) {
            if (isRegistrationUniquenessViolation(e)) {
                throw new PublicRegistrationUnavailableException();
            }
            throw e;
        }
    }

    @Transactional
    public void verifyPublicRegistrationToken(String tokenHash, Instant now) {
        var token = emailTokens.findByTokenHash(tokenHash)
                .orElseThrow(EmailVerificationInvalidException::new);

        if (!PURPOSE_PUBLIC_REGISTRATION.equals(token.getPurpose())
                || token.getUsedAt() != null
                || !token.getExpiresAt().isAfter(now)) {
            throw new EmailVerificationInvalidException();
        }

        var user = users.findByIdForUpdate(token.getUserId())
                .orElseThrow(EmailVerificationInvalidException::new);

        if (!"NORMAL".equals(user.getAccountStatus())
                || !"REGISTERED_USER".equals(user.getPlatformRole())
                || !"PUBLIC".equals(user.getRegistrationSource())
                || user.getEmailVerifiedAt() != null
                || !token.getTargetEmailNormalized().equals(user.getEmailNormalized())
                || !memberships.findByUserIdAndStatus(user.getId(), "ACTIVE").isEmpty()) {
            throw new EmailVerificationInvalidException();
        }

        user.setEmailVerifiedAt(now);
        user.setUpdatedAt(now);
        token.markUsed(now);
        emailTokens.markUnusedTokensUsed(user.getId(), PURPOSE_PUBLIC_REGISTRATION, now);
    }

    @Transactional
    public Optional<PendingVerificationMail> createResendTokenIfEligible(
            String emailNormalized, String rawToken, String tokenHash, Duration ttl, Instant now) {
        var user = users.findByEmailNormalizedForUpdate(emailNormalized);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        var entity = user.orElseThrow();
        if (!"NORMAL".equals(entity.getAccountStatus())
                || !"REGISTERED_USER".equals(entity.getPlatformRole())
                || !"PUBLIC".equals(entity.getRegistrationSource())
                || entity.getEmailVerifiedAt() != null
                || !memberships.findByUserIdAndStatus(entity.getId(), "ACTIVE").isEmpty()) {
            return Optional.empty();
        }

        emailTokens.markUnusedTokensUsed(entity.getId(), PURPOSE_PUBLIC_REGISTRATION, now);
        emailTokens.saveAndFlush(new EmailVerificationTokenEntity(
                UUID.randomUUID(),
                entity.getId(),
                tokenHash,
                PURPOSE_PUBLIC_REGISTRATION,
                emailNormalized,
                now.plus(ttl),
                null,
                now));
        return Optional.of(new PendingVerificationMail(emailNormalized, rawToken));
    }

    private boolean isRegistrationUniquenessViolation(DataIntegrityViolationException e) {
        String text = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();
        return text.contains("uq_users_username")
                || text.contains("uq_users_username_ci")
                || text.contains("uq_users_email_normalized");
    }
}
