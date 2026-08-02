package com.campusguinness.infrastructure.security.session;

import com.campusguinness.identity.application.port.UserSessionRevocationPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Revokes all Spring Session JDBC sessions for a given principal name.
 * <p>
 * Each {@code deleteById} runs in a Spring Session-managed REQUIRES_NEW
 * transaction, independent of the caller's user-status transaction.
 * Failures on individual session deletions are collected; if any fail,
 * a {@link SessionRevocationException} is thrown after all attempts.
 */
@Component
class SpringSessionUserSessionRevoker implements UserSessionRevocationPort {

    private static final Logger log = LoggerFactory.getLogger(SpringSessionUserSessionRevoker.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    SpringSessionUserSessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void revokeAllSessions(String principalName) {
        Map<String, ? extends Session> sessions = sessionRepository
                .findByIndexNameAndIndexValue(
                        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                        principalName);

        if (sessions.isEmpty()) {
            return;
        }

        List<String> failedIds = new ArrayList<>();
        int revoked = 0;

        for (String sessionId : sessions.keySet()) {
            try {
                sessionRepository.deleteById(sessionId);
                revoked++;
            } catch (RuntimeException e) {
                failedIds.add(sessionId);
                log.warn("Failed to delete one session for user {}", principalName, e);
            }
        }

        log.info("Session revocation for {}: found={}, revoked={}, failed={}",
                principalName, sessions.size(), revoked, failedIds.size());

        if (!failedIds.isEmpty()) {
            throw new SessionRevocationException(principalName, sessions.size(), revoked, failedIds);
        }
    }
}
