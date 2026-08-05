package com.campusguinness.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

public final class SchoolAdminInvitation {

    public static final String ROLE_IN_SCHOOL = "SCHOOL_ADMIN";

    private final SchoolAdminInvitationId id;
    private final UUID userId;
    private final UUID schoolId;
    private final String invitationCodeHash;
    private final Instant expiresAt;
    private final UUID createdBy;
    private final int maxAttempts;
    private SchoolAdminInvitationStatus status;
    private Instant acceptedAt;
    private Instant revokedAt;
    private int failedAttempts;

    private SchoolAdminInvitation(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.schoolId = b.schoolId;
        this.invitationCodeHash = b.invitationCodeHash;
        this.expiresAt = b.expiresAt;
        this.createdBy = b.createdBy;
        this.maxAttempts = b.maxAttempts != null ? b.maxAttempts : 5;
        this.status = b.status != null ? b.status : SchoolAdminInvitationStatus.PENDING;
        this.acceptedAt = b.acceptedAt;
        this.revokedAt = b.revokedAt;
        this.failedAttempts = b.failedAttempts != null ? b.failedAttempts : 0;
    }

    public static SchoolAdminInvitation create(Builder builder) {
        validate(builder);
        return new SchoolAdminInvitation(builder);
    }

    public static SchoolAdminInvitation reconstitute(Builder builder) {
        validate(builder);
        if (builder.status == null) {
            throw new IllegalArgumentException("status required for reconstitute");
        }
        return new SchoolAdminInvitation(builder);
    }

    public void accept(Instant acceptedAt) {
        ensurePending("accept");
        if (acceptedAt == null) {
            throw new IllegalArgumentException("acceptedAt required");
        }
        this.status = SchoolAdminInvitationStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    public void revoke(Instant revokedAt) {
        ensurePending("revoke");
        if (revokedAt == null) {
            throw new IllegalArgumentException("revokedAt required");
        }
        this.status = SchoolAdminInvitationStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    public void expire() {
        ensurePending("expire");
        this.status = SchoolAdminInvitationStatus.EXPIRED;
    }

    public void recordFailedAttempt(Instant now) {
        ensurePending("record failed attempt");
        if (now == null) {
            throw new IllegalArgumentException("now required");
        }
        this.failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            this.status = SchoolAdminInvitationStatus.REVOKED;
            this.revokedAt = now;
        }
    }

    public boolean isExpiredAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now required");
        }
        return !expiresAt.isAfter(now);
    }

    private void ensurePending(String action) {
        if (status != SchoolAdminInvitationStatus.PENDING) {
            throw new InvalidSchoolAdminInvitationStateTransitionException(status, action);
        }
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.userId == null) throw new IllegalArgumentException("userId required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.createdBy == null) throw new IllegalArgumentException("createdBy required");
        if (b.expiresAt == null) throw new IllegalArgumentException("expiresAt required");
        if (b.invitationCodeHash == null || b.invitationCodeHash.isBlank()) {
            throw new IllegalArgumentException("invitationCodeHash required");
        }
        int maxAttempts = b.maxAttempts != null ? b.maxAttempts : 5;
        int failedAttempts = b.failedAttempts != null ? b.failedAttempts : 0;
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        if (failedAttempts < 0) throw new IllegalArgumentException("failedAttempts must not be negative");
        if (failedAttempts > maxAttempts) throw new IllegalArgumentException("failedAttempts cannot exceed maxAttempts");
    }

    public SchoolAdminInvitationId id() { return id; }
    public UUID userId() { return userId; }
    public UUID schoolId() { return schoolId; }
    public String roleInSchool() { return ROLE_IN_SCHOOL; }
    public String invitationCodeHash() { return invitationCodeHash; }
    public Instant expiresAt() { return expiresAt; }
    public UUID createdBy() { return createdBy; }
    public SchoolAdminInvitationStatus status() { return status; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant revokedAt() { return revokedAt; }
    public int failedAttempts() { return failedAttempts; }
    public int maxAttempts() { return maxAttempts; }

    public static class Builder {
        private SchoolAdminInvitationId id;
        private UUID userId;
        private UUID schoolId;
        private String invitationCodeHash;
        private Instant expiresAt;
        private UUID createdBy;
        private SchoolAdminInvitationStatus status;
        private Instant acceptedAt;
        private Instant revokedAt;
        private Integer failedAttempts;
        private Integer maxAttempts;

        public Builder id(SchoolAdminInvitationId v) { this.id = v; return this; }
        public Builder userId(UUID v) { this.userId = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder invitationCodeHash(String v) { this.invitationCodeHash = v; return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder createdBy(UUID v) { this.createdBy = v; return this; }
        public Builder status(SchoolAdminInvitationStatus v) { this.status = v; return this; }
        public Builder acceptedAt(Instant v) { this.acceptedAt = v; return this; }
        public Builder revokedAt(Instant v) { this.revokedAt = v; return this; }
        public Builder failedAttempts(Integer v) { this.failedAttempts = v; return this; }
        public Builder maxAttempts(Integer v) { this.maxAttempts = v; return this; }
    }
}
