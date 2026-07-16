package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaPlatformTakedown(MediaId mediaId, boolean triggeredByInternalDisable, Instant occurredAt) {
    public MediaPlatformTakedown(MediaId id, boolean triggeredByInternalDisable) { this(id, triggeredByInternalDisable, Instant.now()); }
}
