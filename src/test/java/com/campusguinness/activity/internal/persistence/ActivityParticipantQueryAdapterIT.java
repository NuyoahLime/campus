package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.activity.application.exception.ActivityParticipantAlreadyAssignedException;
import com.campusguinness.activity.internal.domain.ActivityParticipant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityParticipantQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ActivityParticipantQueryAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private final String prefix = "s25pa-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM activity_participants
                WHERE activity_id IN (
                    SELECT a.id FROM activities a
                    JOIN schools s ON s.id = a.school_id
                    WHERE s.name LIKE ?
                )
                """, prefix + "%");
        jdbc.update("""
                DELETE FROM activities
                WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)
                """, prefix + "%");
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                """, prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", prefix + "%");
    }

    @Test
    void translatesTheParticipantUniqueConstraintToTheDomainConflict() {
        UUID schoolId = insertSchool();
        UUID adminId = insertUser("admin");
        UUID studentMembershipId = insertMembership(insertUser("student"), schoolId, "STUDENT");
        UUID activityId = insertActivity(schoolId, adminId);

        adapter.save(ActivityParticipant.assign(activityId, studentMembershipId, Instant.now()));

        assertThatThrownBy(() -> adapter.save(
                ActivityParticipant.assign(activityId, studentMembershipId, Instant.now())))
                .isInstanceOf(ActivityParticipantAlreadyAssignedException.class);
    }

    private UUID insertSchool() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,
                                    address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix + "-school", "USCC", prefix + "-uc-" + suffix, prefix + "-ic-" + suffix,
                "UNIVERSITY", "Region", "Address", "Contact", "13800000000", prefix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                id, prefix + "-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL");
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status)
                VALUES (?,?,?,?,?)
                """, id, userId, schoolId, role, "ACTIVE");
        return id;
    }

    private UUID insertActivity(UUID schoolId, UUID adminId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by)
                VALUES (?,?,?,?,?,?)
                """, id, schoolId, prefix + "-activity", "PUBLISHED", "NOT_SUBMITTED", adminId);
        return id;
    }
}
