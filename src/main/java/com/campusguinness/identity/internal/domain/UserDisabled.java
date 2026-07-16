package com.campusguinness.identity.internal.domain;
import java.time.Instant;
public record UserDisabled(UserId userId, Instant occurredAt) {
    public UserDisabled(UserId id) { this(id, Instant.now()); }
}
