package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsibleTeacherAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ResponsibleTeacherAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId, userId, teacher1Id, teacher2Id;
    UUID membership1, membership2, projectId, ruleVersionId, actId, apId;
    final List<UUID> createdAssignmentIds = new ArrayList<>();
    final List<UUID> createdProjectIds = new ArrayList<>();
    final List<UUID> createdSchoolIds = new ArrayList<>();
    final List<UUID> createdUserIds = new ArrayList<>();
    final List<UUID> createdMembershipIds = new ArrayList<>();

    @BeforeEach void setUp() {
        schoolId = UUID.randomUUID(); userId = UUID.randomUUID();
        teacher1Id = UUID.randomUUID(); teacher2Id = UUID.randomUUID();
        membership1 = UUID.randomUUID(); membership2 = UUID.randomUUID();
        projectId = UUID.randomUUID(); ruleVersionId = UUID.randomUUID();
        actId = UUID.randomUUID(); apId = UUID.randomUUID();

        createdSchoolIds.add(schoolId);
        createdUserIds.addAll(List.of(userId, teacher1Id, teacher2Id));
        createdMembershipIds.addAll(List.of(membership1, membership2));
        createdProjectIds.addAll(List.of(projectId, actId, apId));

        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "School", "USCC", "UC", "INT", "PRIMARY", "BJ", "addr", "n", "p", "e", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", userId, "u", "hash", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacher1Id, "te1", "hash", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacher2Id, "te2", "hash", "NORMAL");

        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", membership1, teacher1Id, schoolId, "TEACHER", "ACTIVE");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", membership2, teacher2Id, schoolId, "TEACHER", "ACTIVE");
        jdbc.update("INSERT INTO teacher_profiles(membership_id,subject,title) VALUES (?,?,?)", membership1, "Physics", "Head");

        // Create real challenge_project and rule version for FK
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "P", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", true, "BEST", "PUBLISHED");
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,created_by) VALUES (?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", userId);

        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?)",
                actId, schoolId, "t", "DRAFT", "NOT_SUBMITTED", userId, Instant.now(), Instant.now(), 1);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                apId, actId, projectId, ruleVersionId);
    }

    @AfterEach void tearDown() {
        for (UUID aid : createdAssignmentIds) { jdbc.update("DELETE FROM responsible_teachers WHERE id=?", aid); }
        createdAssignmentIds.clear();
        for (UUID pid : createdProjectIds) {
            jdbc.update("DELETE FROM activity_projects WHERE id=?", pid);
            jdbc.update("DELETE FROM activities WHERE id=?", pid);
        }
        createdProjectIds.clear();
        jdbc.update("DELETE FROM teacher_profiles WHERE membership_id IN (?,?)", membership1, membership2);
        for (UUID cid : List.of(projectId)) {
            jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", cid);
            jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", cid);
            jdbc.update("DELETE FROM challenge_projects WHERE id=?", cid);
        }
        for (UUID mid : createdMembershipIds) { jdbc.update("DELETE FROM school_memberships WHERE id=?", mid); }
        for (UUID uid : createdUserIds) { jdbc.update("DELETE FROM users WHERE id=?", uid); }
        for (UUID sid : createdSchoolIds) { jdbc.update("DELETE FROM schools WHERE id=?", sid); }
    }

    @Test void assignSavesMembershipIdAndReturnsRichRecord() {
        var record = adapter.assign(apId, membership1, teacher1Id);
        createdAssignmentIds.add(record.id());
        assertThat(record.teacherMembershipId()).isEqualTo(membership1);
        assertThat(record.username()).isEqualTo("te1");
        assertThat(record.subject()).isEqualTo("Physics");
        assertThat(record.title()).isEqualTo("Head");
        assertThat(record.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(record.accountStatus()).isEqualTo("NORMAL");
    }

    @Test void findByActivityProjectReturnsAssignedTeachers() {
        var r = adapter.assign(apId, membership1, teacher1Id);
        createdAssignmentIds.add(r.id());
        var list = adapter.findByActivityProject(apId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).username()).isEqualTo("te1");
    }

    @Test void countAssignableCountsActiveNormalOnly() {
        var r = adapter.assign(apId, membership1, teacher1Id);
        createdAssignmentIds.add(r.id());
        var counts = adapter.countAssignableByActivityProjects(List.of(apId));
        assertThat(counts.getOrDefault(apId, 0L)).isEqualTo(1);
    }

    @Test void unassignByIdRemovesAssignment() {
        var r = adapter.assign(apId, membership1, teacher1Id);
        adapter.unassignById(r.id());
        assertThat(adapter.findByActivityProject(apId)).isEmpty();
    }

    @Test void deleteAllByActivityProjectClearsAll() {
        adapter.assign(apId, membership1, teacher1Id);
        adapter.assign(apId, membership2, teacher2Id);
        // Both assignments created; deleteAll must clear both
        adapter.deleteAllByActivityProject(apId);
        assertThat(adapter.findByActivityProject(apId)).isEmpty();
    }
}
