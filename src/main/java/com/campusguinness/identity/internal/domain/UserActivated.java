package com.campusguinness.identity.internal.domain;
import java.time.Instant;
public record UserActivated(UserId userId, Instant occurredAt) {
    public UserActivated(UserId id) { this(id, Instant.now()); }
}
