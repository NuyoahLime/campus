package com.campusguinness.ranking.internal.domain;
import java.util.UUID;
public record L3AuthorizationId(UUID value) {
    public L3AuthorizationId { if (value == null) throw new IllegalArgumentException("id must not be null"); }
}
