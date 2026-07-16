package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaMadePublic(MediaId mediaId, Instant occurredAt) {
    public MediaMadePublic(MediaId id) { this(id, Instant.now()); }
}
