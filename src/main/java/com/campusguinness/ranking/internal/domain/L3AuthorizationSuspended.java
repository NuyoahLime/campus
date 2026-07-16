package com.campusguinness.ranking.internal.domain;
import java.time.Instant;
public record L3AuthorizationSuspended(L3AuthorizationId authorizationId, Instant occurredAt) {
    public L3AuthorizationSuspended(L3AuthorizationId id) { this(id, Instant.now()); }
}
