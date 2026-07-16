package com.campusguinness.identity.internal.domain;
import java.util.UUID;
public record UserId(UUID value) {
    public UserId { if (value == null) throw new IllegalArgumentException("id must not be null"); }
}
