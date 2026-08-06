package com.campusguinness.audit.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
public class AuditRecordEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_id")
    private UUID schoolId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditRecordEntity() {}

    void setId(UUID id) { this.id = id; }
    void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }
    void setActorId(UUID actorId) { this.actorId = actorId; }
    void setAction(String action) { this.action = action; }
    void setTargetType(String targetType) { this.targetType = targetType; }
    void setTargetId(UUID targetId) { this.targetId = targetId; }
    void setDetail(String detail) { this.detail = detail; }
    void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
