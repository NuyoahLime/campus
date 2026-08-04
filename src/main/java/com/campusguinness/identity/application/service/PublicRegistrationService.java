package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.port.SecureTokenGenerator;
import com.campusguinness.identity.application.port.SecureTokenHasher;
import com.campusguinness.identity.application.port.PasswordPolicy;
import com.campusguinness.identity.internal.persistence.PublicRegistrationPersistenceService;
import com.campusguinness.infrastructure.security.AuthTokenProperties;
import com.campusguinness.infrastructure.security.LoginNameNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class PublicRegistrationService {

    private final LoginNameNormalizer loginNameNormalizer;
    private final EmailNormalizer emailNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final SecureTokenHasher tokenHasher;
    private final AuthTokenProperties tokenProperties;
    private final PublicRegistrationPersistenceService persistence;
    private final VerificationMailService verificationMailService;
    private final ResendVerificationRateLimiter resendRateLimiter;
    private final Clock clock;

    public PublicRegistrationService(LoginNameNormalizer loginNameNormalizer,
            EmailNormalizer emailNormalizer,
            PasswordEncoder passwordEncoder,
            SecureTokenGenerator tokenGenerator,
            SecureTokenHasher tokenHasher,
            AuthTokenProperties tokenProperties,
            PublicRegistrationPersistenceService persistence,
            VerificationMailService verificationMailService,
            ResendVerificationRateLimiter resendRateLimiter,
            Clock clock) {
        this.loginNameNormalizer = loginNameNormalizer;
        this.emailNormalizer = emailNormalizer;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.tokenProperties = tokenProperties;
        this.persistence = persistence;
        this.verificationMailService = verificationMailService;
        this.resendRateLimiter = resendRateLimiter;
        this.clock = clock;
    }

    public PublicRegistrationResult register(String username, String email,
            String password, String confirmPassword) {
        String normalizedUsername = loginNameNormalizer.normalize(username);
        String normalizedEmail = emailNormalizer.normalize(email);
        validatePasswordPair(password, confirmPassword);

        String rawToken = tokenGenerator.generate();
        persistence.createPublicRegistration(
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(password),
                tokenHasher.hash(rawToken),
                tokenProperties.emailVerificationTtl(),
                clock.instant());
        trySend(normalizedEmail, rawToken);
        return PublicRegistrationResult.verifyEmail(normalizedUsername);
    }

    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new EmailVerificationInvalidException();
        }
        persistence.verifyPublicRegistrationToken(tokenHasher.hash(rawToken), clock.instant());
    }

    public ResendVerificationResponse resendVerification(String email, String clientIp) {
        String normalizedEmail = emailNormalizer.normalize(email);
        if (resendRateLimiter.isLimitedAndRecord(normalizedEmail, clientIp)) {
            return ResendVerificationResponse.generic();
        }

        String rawToken = tokenGenerator.generate();
        var pending = persistence.createResendTokenIfEligible(
                normalizedEmail,
                rawToken,
                tokenHasher.hash(rawToken),
                tokenProperties.emailVerificationTtl(),
                clock.instant());
        pending.ifPresent(mail -> trySend(mail.email(), mail.rawToken()));
        return ResendVerificationResponse.generic();
    }

    private void validatePasswordPair(String password, String confirmPassword) {
        PasswordPolicy.validate(password);
        if (!password.equals(confirmPassword)) {
            throw new InvalidPasswordException("PASSWORD_MISMATCH");
        }
    }

    private void trySend(String email, String rawToken) {
        try {
            verificationMailService.sendVerificationMail(email, rawToken);
        } catch (RuntimeException ignored) {
            // Registration state is deliberately retained; no raw token or full URL is logged.
        }
    }
}
