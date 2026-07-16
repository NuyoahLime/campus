package com.campusguinness.audit.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "audit_records")
public class AuditRecordEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id") private UUID schoolId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "action", nullable = false, length = 64) private String action;
    @Column(name = "target_type", nullable = false, length = 32) private String targetType;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Column(name = "detail", columnDefinition = "jsonb") private String detail;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected AuditRecordEntity() {}
}
