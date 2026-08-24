package com.campusguinness.activity.internal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An individual student assignment made by a school administrator.
 *
 * <p>This aggregate is deliberately separate from ActivityApplication:
 * ActivityApplication remains the activity creation application workflow.
 */
public final class ActivityParticipant {
    private final UUID id;
    private final UUID activityId;
    private final UUID studentMembershipId;
    private final Instant createdAt;

    private ActivityParticipant(UUID id, UUID activityId, UUID studentMembershipId, Instant createdAt) {
        this.id = require(id, "id");
        this.activityId = require(activityId, "activityId");
        this.studentMembershipId = require(studentMembershipId, "studentMembershipId");
        this.createdAt = require(createdAt, "createdAt");
    }

    public static ActivityParticipant assign(UUID activityId, UUID studentMembershipId, Instant createdAt) {
        return new ActivityParticipant(UUID.randomUUID(), activityId, studentMembershipId, createdAt);
    }

    public static ActivityParticipant reconstitute(UUID id, UUID activityId,
                                                    UUID studentMembershipId, Instant createdAt) {
        return new ActivityParticipant(id, activityId, studentMembershipId, createdAt);
    }

    public UUID id() { return id; }
    public UUID activityId() { return activityId; }
    public UUID studentMembershipId() { return studentMembershipId; }
    public Instant createdAt() { return createdAt; }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
