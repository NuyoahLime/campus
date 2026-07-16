package com.campusguinness.media.internal.domain;
import java.time.Instant;
public record MediaInternalReviewSubmitted(MediaId mediaId, Instant occurredAt) {
    public MediaInternalReviewSubmitted(MediaId id) { this(id, Instant.now()); }
}
