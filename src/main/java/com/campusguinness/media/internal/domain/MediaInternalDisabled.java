package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaInternalDisabled(MediaId mediaId, boolean publicAutoTakedown, Instant occurredAt) {
    public MediaInternalDisabled(MediaId id, boolean publicAutoTakedown) { this(id, publicAutoTakedown, Instant.now()); }
}
