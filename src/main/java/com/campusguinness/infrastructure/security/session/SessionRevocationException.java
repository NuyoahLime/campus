package com.campusguinness.infrastructure.security.session;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when one or more sessions could not be deleted during revocation.
 * Partial success is possible: some sessions may have been deleted while
 * others failed. The caller must decide how to handle this state.
 */
public class SessionRevocationException extends RuntimeException {

    private final String principalName;
    private final int foundCount;
    private final int revokedCount;
    private final List<String> failedSessionIds;

    public SessionRevocationException(String principalName, int foundCount,
            int revokedCount, List<String> failedSessionIds) {
        super("Session revocation incomplete for " + principalName
                + ": revoked " + revokedCount + "/" + foundCount
                + ", failed " + failedSessionIds.size());
        this.principalName = principalName;
        this.foundCount = foundCount;
        this.revokedCount = revokedCount;
        this.failedSessionIds = Collections.unmodifiableList(failedSessionIds);
    }

    public String getPrincipalName() { return principalName; }
    public int getFoundCount() { return foundCount; }
    public int getRevokedCount() { return revokedCount; }
    public List<String> getFailedSessionIds() { return failedSessionIds; }
}
