package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose;

    @Column(name = "target_email_normalized", nullable = false, length = 320)
    private String targetEmailNormalized;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected EmailVerificationTokenEntity() {}

    public EmailVerificationTokenEntity(UUID id, UUID userId, String tokenHash, String purpose,
            String targetEmailNormalized, Instant expiresAt, Instant usedAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.targetEmailNormalized = targetEmailNormalized;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public String getPurpose() { return purpose; }
    public String getTargetEmailNormalized() { return targetEmailNormalized; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
