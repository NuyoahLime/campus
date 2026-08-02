package com.campusguinness.identity.application.port;

/**
 * Application-layer port for revoking all active sessions of a user.
 * Infrastructure implements this using Spring Session JDBC.
 * Domain layer must never depend on this.
 */
public interface UserSessionRevocationPort {

    /**
     * Revoke all sessions for the given principal name (username).
     * Must not throw if no sessions exist.
     *
     * @param principalName the normalized username whose sessions should be revoked
     */
    void revokeAllSessions(String principalName);
}
