package com.campusguinness.ranking.internal.domain;
import java.time.Instant;
public record L3AuthorizationWithdrawn(L3AuthorizationId authorizationId, boolean terminal, Instant occurredAt) {
    public L3AuthorizationWithdrawn(L3AuthorizationId id) { this(id, true, Instant.now()); }
}
