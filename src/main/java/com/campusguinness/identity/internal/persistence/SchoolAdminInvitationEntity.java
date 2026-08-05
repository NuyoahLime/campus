package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_admin_invitations")
public class SchoolAdminInvitationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "role_in_school", nullable = false, length = 32)
    private String roleInSchool;

    @Column(name = "invitation_code_hash", nullable = false, length = 255)
    private String invitationCodeHash;

    @Column(name = "invitation_status", nullable = false, length = 32)
    private String invitationStatus;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected SchoolAdminInvitationEntity() {}

    void setId(UUID v) { id = v; }
    void setUserId(UUID v) { userId = v; }
    void setSchoolId(UUID v) { schoolId = v; }
    void setRoleInSchool(String v) { roleInSchool = v; }
    void setInvitationCodeHash(String v) { invitationCodeHash = v; }
    void setInvitationStatus(String v) { invitationStatus = v; }
    void setExpiresAt(Instant v) { expiresAt = v; }
    void setAcceptedAt(Instant v) { acceptedAt = v; }
    void setRevokedAt(Instant v) { revokedAt = v; }
    void setCreatedBy(UUID v) { createdBy = v; }
    void setFailedAttempts(int v) { failedAttempts = v; }
    void setMaxAttempts(int v) { maxAttempts = v; }
    void setCreatedAt(Instant v) { createdAt = v; }
    void setUpdatedAt(Instant v) { updatedAt = v; }
    void setVersion(int v) { version = v; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSchoolId() { return schoolId; }
    public String getRoleInSchool() { return roleInSchool; }
    public String getInvitationCodeHash() { return invitationCodeHash; }
    public String getInvitationStatus() { return invitationStatus; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public int getFailedAttempts() { return failedAttempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
