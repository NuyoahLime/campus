package com.campusguinness.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Coordinates account lockout mechanism.
 * When login-lockout.enabled=false, all methods are safe no-ops.
 */
@Service
@Transactional
public class LoginSecurityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginSecurityApplicationService.class);

    private final LoginLockoutProperties props;
    private final AuthStateRepository repo;
    private final LoginIdentifierNormalizer normalizer;
    private final Clock clock;

    public LoginSecurityApplicationService(LoginLockoutProperties props, AuthStateRepository repo,
                                            LoginIdentifierNormalizer normalizer, Clock clock) {
        this.props = props;
        this.repo = repo;
        this.normalizer = normalizer;
        this.clock = clock;
    }

    /** Called before password authentication. Auto-unlocks expired locks. */
    public void beforeAuthentication(String rawLoginName) {
        if (!props.enabled()) return;
        String name = normalizer.normalize(rawLoginName);
        if (name.isEmpty()) return;
        repo.unlockIfExpired(name);
    }

    /** Called on BadCredentialsException. Atomically increments and locks if threshold reached. */
    public void recordBadCredentials(String rawLoginName) {
        if (!props.enabled()) return;
        String name = normalizer.normalize(rawLoginName);
        if (name.isEmpty()) return;

        Instant lockExpiry = Instant.now(clock).plus(props.lockDuration());
        int updated = repo.incrementAndLockIfNeeded(name, props.failureThreshold(), lockExpiry);
        if (updated > 0) {
            log.debug("Bad credentials recorded for: {}", name);
        }
    }

    /** Called after successful authentication. Resets failure count. */
    public void recordSuccessfulLogin(UUID authenticatedUserId) {
        if (!props.enabled() || !props.resetFailuresOnSuccess()) return;
        repo.resetFailures(authenticatedUserId);
    }
}
