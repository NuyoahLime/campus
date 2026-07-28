package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsibleTeacherAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ResponsibleTeacherAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId, userId, teacherUserId;
    UUID teacherMembershipId, apId;
    final List<UUID> createdAssignmentIds = new ArrayList<>();
    final List<UUID> createdProjectIds = new ArrayList<>();
    final List<UUID> createdSchoolIds = new ArrayList<>();
    final List<UUID> createdUserIds = new ArrayList<>();
    final List<UUID> createdMembershipIds = new ArrayList<>();

    @BeforeEach void setUp() {
        schoolId = UUID.randomUUID(); userId = UUID.randomUUID(); teacherUserId = UUID.randomUUID();
        teacherMembershipId = UUID.randomUUID(); apId = UUID.randomUUID();
        createdSchoolIds.add(schoolId); createdUserIds.addAll(List.of(userId, teacherUserId));
        createdMembershipIds.add(teacherMembershipId); createdProjectIds.add(apId);

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "School", "USCC", "UC", "INT", "PRIMARY", "BJ", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", userId, "u", "hash", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacherUserId, "te", "hash", "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", teacherMembershipId, teacherUserId, schoolId, "TEACHER", "ACTIVE");
        jdbc.update("INSERT INTO teacher_profiles(membership_id,subject,title) VALUES (?,?,?)", teacherMembershipId, "Physics", "Head");

        // Create a dummy activity_project
        UUID actId = UUID.randomUUID(); createdProjectIds.add(actId);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                actId, schoolId, "t", "DRAFT", "NOT_SUBMITTED", userId, java.time.Instant.now(), java.time.Instant.now(), 1);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                apId, actId, UUID.randomUUID(), UUID.randomUUID());
    }

    @AfterEach void tearDown() {
        for (UUID aid : createdAssignmentIds) { jdbc.update("DELETE FROM responsible_teachers WHERE id=?", aid); }
        for (UUID pid : createdProjectIds) { jdbc.update("DELETE FROM activity_projects WHERE id=?", pid); jdbc.update("DELETE FROM activities WHERE id=?", pid); }
        for (UUID mid : createdMembershipIds) { jdbc.update("DELETE FROM school_memberships WHERE id=?", mid); }
        for (UUID uid : createdUserIds) { jdbc.update("DELETE FROM users WHERE id=?", uid); }
        for (UUID sid : createdSchoolIds) { jdbc.update("DELETE FROM schools WHERE id=?", sid); }
    }

    @Test void assignSavesMembershipIdAndReturnsRichRecord() {
        var record = adapter.assign(apId, teacherMembershipId, teacherUserId);
        createdAssignmentIds.add(record.id());
        assertThat(record.teacherMembershipId()).isEqualTo(teacherMembershipId);
        assertThat(record.username()).isEqualTo("te");
        assertThat(record.subject()).isEqualTo("Physics");
        assertThat(record.title()).isEqualTo("Head");
        assertThat(record.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(record.accountStatus()).isEqualTo("NORMAL");
    }

    @Test void findByActivityProjectReturnsAssignedTeachers() {
        var r = adapter.assign(apId, teacherMembershipId, teacherUserId);
        createdAssignmentIds.add(r.id());
        var list = adapter.findByActivityProject(apId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).username()).isEqualTo("te");
    }

    @Test void countAssignableCountsActiveNormalOnly() {
        var r = adapter.assign(apId, teacherMembershipId, teacherUserId);
        createdAssignmentIds.add(r.id());
        var counts = adapter.countAssignableByActivityProjects(List.of(apId));
        assertThat(counts.getOrDefault(apId, 0L)).isEqualTo(1);
    }

    @Test void unassignByIdRemovesAssignment() {
        var r = adapter.assign(apId, teacherMembershipId, teacherUserId);
        adapter.unassignById(r.id());
        assertThat(adapter.findByActivityProject(apId)).isEmpty();
    }

    @Test void deleteAllByActivityProjectClearsAll() {
        adapter.assign(apId, teacherMembershipId, teacherUserId);
        adapter.assign(apId, teacherMembershipId, teacherUserId); // will be new ID due to dup check
        // Just test deleteAll
        adapter.deleteAllByActivityProject(apId);
        assertThat(adapter.findByActivityProject(apId)).isEmpty();
    }
}
