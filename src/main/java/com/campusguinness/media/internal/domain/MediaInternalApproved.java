package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaInternalApproved(MediaId mediaId, Instant occurredAt) {
    public MediaInternalApproved(MediaId id) { this(id, Instant.now()); }
}
