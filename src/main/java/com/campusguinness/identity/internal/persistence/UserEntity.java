package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "account_status", nullable = false, length = 32)
    private String accountStatus;

    @Column(name = "platform_role", length = 32)
    private String platformRole;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "login_failures", nullable = false)
    private int loginFailures;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected UserEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setUsername(String v) { username = v; } public String getUsername() { return username; }
    void setPasswordHash(String v) { passwordHash = v; } public String getPasswordHash() { return passwordHash; }
    void setAccountStatus(String v) { accountStatus = v; } public String getAccountStatus() { return accountStatus; }
    void setPlatformRole(String v) { platformRole = v; } public String getPlatformRole() { return platformRole; }
    void setLockedUntil(Instant v) { lockedUntil = v; } public Instant getLockedUntil() { return lockedUntil; }
    void setLoginFailures(int v) { loginFailures = v; } public int getLoginFailures() { return loginFailures; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
