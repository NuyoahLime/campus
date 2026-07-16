package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_memberships")
public class SchoolMembershipEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "role_in_school", nullable = false, length = 32) private String roleInSchool;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected SchoolMembershipEntity() {}
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSchoolId() { return schoolId; }
    public String getRoleInSchool() { return roleInSchool; }
}
