package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaInternalRejected(MediaId mediaId, Instant occurredAt) {
    public MediaInternalRejected(MediaId id) { this(id, Instant.now()); }
}
