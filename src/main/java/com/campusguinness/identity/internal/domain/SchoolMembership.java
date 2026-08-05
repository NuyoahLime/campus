package com.campusguinness.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

/** School membership child entity of the User aggregate. References School by ID only. */
public final class SchoolMembership {

    private final SchoolMembershipId id;
    private final UUID schoolId;
    private final SchoolRole role;
    private MembershipStatus status;
    private final Instant startedAt;
    private Instant endedAt;
    private final int version;

    private SchoolMembership(
            SchoolMembershipId id,
            UUID schoolId,
            SchoolRole role,
            MembershipStatus status,
            Instant startedAt,
            Instant endedAt,
            int version
    ) {
        this.id = require(id, "schoolMembershipId required");
        this.schoolId = require(schoolId, "schoolId required");
        this.role = require(role, "role required");
        this.status = require(status, "status required");
        this.startedAt = require(startedAt, "startedAt required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        validateEndedAt(status, startedAt, endedAt);
        this.endedAt = endedAt;
        this.version = version;
    }

    public static SchoolMembership start(
            SchoolMembershipId id,
            UUID schoolId,
            SchoolRole role,
            Instant startedAt
    ) {
        if (role == SchoolRole.TEACHER) {
            throw new IllegalArgumentException("new TEACHER membership is not allowed");
        }
        return new SchoolMembership(id, schoolId, role, MembershipStatus.ACTIVE, startedAt, null, 0);
    }

    public static SchoolMembership reconstitute(
            SchoolMembershipId id,
            UUID schoolId,
            SchoolRole role,
            MembershipStatus status,
            Instant startedAt,
            Instant endedAt,
            int version
    ) {
        return new SchoolMembership(id, schoolId, role, status, startedAt, endedAt, version);
    }

    public void end(Instant endedAt) {
        if (status != MembershipStatus.ACTIVE) {
            throw new InvalidSchoolMembershipStateTransitionException(status, "end");
        }
        validateEndedAt(MembershipStatus.ENDED, startedAt, endedAt);
        this.status = MembershipStatus.ENDED;
        this.endedAt = endedAt;
    }

    private static void validateEndedAt(MembershipStatus status, Instant startedAt, Instant endedAt) {
        if (status == MembershipStatus.ACTIVE && endedAt != null) {
            throw new IllegalArgumentException("active membership must not have endedAt");
        }
        if (status == MembershipStatus.ENDED && endedAt == null) {
            throw new IllegalArgumentException("endedAt required for ended membership");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public SchoolMembershipId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public SchoolRole role() { return role; }
    public String roleInSchool() { return role.name(); }
    public MembershipStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public int version() { return version; }
}
