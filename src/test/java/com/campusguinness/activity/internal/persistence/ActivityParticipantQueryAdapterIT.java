package com.campusguinness.activity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityParticipantQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ActivityParticipantQueryAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId;
    UUID actorId;
    UUID activityId;
    UUID otherActivityId;
    UUID activityProjectId1;
    UUID activityProjectId2;
    final List<StudentFixture> students = new ArrayList<>();
    final List<UUID> projectIds = new ArrayList<>();
    final List<UUID> ruleVersionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        otherActivityId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO schools(
                    id,name,unified_code_type,unified_code,internal_code,school_type,
                    region,address,contact_name,contact_phone,contact_email,school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, schoolId, "Roster School", "USCC", "ROSTER-" + suffix(),
                "INT-" + suffix(), "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        insertUser(actorId, "roster-admin-" + suffix());

        jdbc.update("""
                INSERT INTO activities(
                    id,school_id,title,execution_status,public_status,created_by,
                    created_at,updated_at,version
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, activityId, schoolId, "Roster Activity", "DRAFT", "NOT_SUBMITTED",
                actorId, ts(Instant.now()), ts(Instant.now()), 1);
        jdbc.update("""
                INSERT INTO activities(
                    id,school_id,title,execution_status,public_status,created_by,
                    created_at,updated_at,version
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, otherActivityId, schoolId, "Other Activity", "DRAFT", "NOT_SUBMITTED",
                actorId, ts(Instant.now()), ts(Instant.now()), 1);

        activityProjectId1 = seedActivityProject(activityId, "Roster Project 1");
        activityProjectId2 = seedActivityProject(activityId, "Roster Project 2");

        students.add(seedStudent("AliceRoster", "G7", "Class 1", "S001"));
        students.add(seedStudent("bobroster", "G8", "Class 2", "S002"));
        students.add(seedStudent("CharlieRoster", null, null, null));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM score_attempts WHERE activity_project_id IN (?,?)",
                activityProjectId1, activityProjectId2);
        jdbc.update("""
                DELETE FROM activity_project_participants
                WHERE activity_project_id IN (?,?)
                """, activityProjectId1, activityProjectId2);
        jdbc.update("DELETE FROM activity_participants WHERE activity_id IN (?,?)",
                activityId, otherActivityId);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (?,?)",
                activityId, otherActivityId);
        jdbc.update("DELETE FROM activities WHERE id IN (?,?)", activityId, otherActivityId);
        for (UUID ruleVersionId : ruleVersionIds) {
            jdbc.update("DELETE FROM project_rule_versions WHERE id=?", ruleVersionId);
        }
        for (UUID projectId : projectIds) {
            jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
        }
        for (StudentFixture student : students) {
            jdbc.update("DELETE FROM student_profiles WHERE membership_id=?", student.membershipId());
        }
        if (!students.isEmpty()) {
            jdbc.update("DELETE FROM school_memberships WHERE school_id=?", schoolId);
            jdbc.update("DELETE FROM users WHERE id IN (?,?,?)",
                    students.get(0).studentId(),
                    students.get(1).studentId(),
                    students.get(2).studentId());
        }
        jdbc.update("DELETE FROM users WHERE id=?", actorId);
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
    }

    @Test
    void listReturnsStudentIdAndUsername() {
        insertParticipant(UUID.randomUUID(), activityId, students.get(0).membershipId(), Instant.now());

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.studentId()).isEqualTo(students.get(0).studentId());
            assertThat(item.displayName()).isEqualTo("AliceRoster");
            assertThat(item.grade()).isEqualTo("G7");
            assertThat(item.className()).isEqualTo("Class 1");
            assertThat(item.studentNumber()).isEqualTo("S001");
        });
    }

    @Test
    void keywordFiltersByUsername() {
        insertParticipant(UUID.randomUUID(), activityId, students.get(0).membershipId(), Instant.now());
        insertParticipant(UUID.randomUUID(), activityId, students.get(1).membershipId(), Instant.now());

        var result = adapter.findByActivity(activityId, "  Alice  ", 0, 20);

        assertThat(result.items()).extracting(item -> item.displayName())
                .containsExactly("AliceRoster");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void keywordIsCaseInsensitive() {
        insertParticipant(UUID.randomUUID(), activityId, students.get(1).membershipId(), Instant.now());

        var result = adapter.findByActivity(activityId, "BOBROSTER", 0, 20);

        assertThat(result.items()).extracting(item -> item.displayName())
                .containsExactly("bobroster");
    }

    @Test
    void paginationReturnsCorrectTotal() {
        Instant now = Instant.now();
        insertParticipant(UUID.randomUUID(), activityId, students.get(0).membershipId(), now.minusSeconds(2));
        insertParticipant(UUID.randomUUID(), activityId, students.get(1).membershipId(), now.minusSeconds(1));
        insertParticipant(UUID.randomUUID(), activityId, students.get(2).membershipId(), now);

        var firstPage = adapter.findByActivity(activityId, null, 0, 2);
        var secondPage = adapter.findByActivity(activityId, null, 1, 2);

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.items()).hasSize(1);
    }

    @Test
    void orderingUsesCreatedAtThenId() {
        Instant sameTime = Instant.parse("2026-07-29T00:00:00Z");
        UUID lowerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        insertParticipant(lowerId, activityId, students.get(0).membershipId(), sameTime);
        insertParticipant(higherId, activityId, students.get(1).membershipId(), sameTime);

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).extracting(item -> item.participantId())
                .containsExactly(higherId, lowerId);
    }

    @Test
    void assignedProjectCountIsAccurate() {
        UUID participantId = UUID.randomUUID();
        insertParticipant(participantId, activityId, students.get(0).membershipId(), Instant.now());
        insertProjectAssignment(activityProjectId1, participantId);
        insertProjectAssignment(activityProjectId2, participantId);

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).singleElement()
                .extracting(item -> item.assignedProjectCount())
                .isEqualTo(2L);
    }

    @Test
    void hasScoreAttemptIsTrueWhenAnyProjectHasAttempt() {
        insertParticipant(UUID.randomUUID(), activityId, students.get(0).membershipId(), Instant.now());
        insertScoreAttempt(activityProjectId2, students.get(0).studentId());

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).singleElement()
                .extracting(item -> item.hasScoreAttempt())
                .isEqualTo(true);
    }

    @Test
    void participantsFromOtherActivityAreExcluded() {
        insertParticipant(UUID.randomUUID(), activityId, students.get(0).membershipId(), Instant.now());
        insertParticipant(UUID.randomUUID(), otherActivityId, students.get(1).membershipId(), Instant.now());

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).extracting(item -> item.studentId())
                .containsExactly(students.get(0).studentId());
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void queryDoesNotDuplicateParticipantsWhenAssignedToMultipleProjects() {
        UUID participantId = UUID.randomUUID();
        insertParticipant(participantId, activityId, students.get(0).membershipId(), Instant.now());
        insertProjectAssignment(activityProjectId1, participantId);
        insertProjectAssignment(activityProjectId2, participantId);

        var result = adapter.findByActivity(activityId, null, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().assignedProjectCount()).isEqualTo(2);
    }

    private StudentFixture seedStudent(String username, String grade, String className, String studentNumber) {
        UUID studentId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        insertUser(studentId, username);
        jdbc.update("""
                INSERT INTO school_memberships(
                    id,user_id,school_id,role_in_school,status,started_at,created_at,version
                ) VALUES (?,?,?,?,?,now(),now(),1)
                """, membershipId, studentId, schoolId, "STUDENT", "ACTIVE");
        jdbc.update("""
                INSERT INTO student_profiles(membership_id,grade,class_name,student_number)
                VALUES (?,?,?,?)
                """, membershipId, grade, className, studentNumber);
        return new StudentFixture(studentId, membershipId);
    }

    private UUID seedActivityProject(UUID targetActivityId, String name) {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        projectIds.add(projectId);
        ruleVersionIds.add(ruleVersionId);
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id,name,category,score_storage_type,score_indicator_type,
                    comparison_direction,allow_tie,effective_score_rule,project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, projectId, name, "SPEED", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", true, "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id,project_id,version_number,score_storage_type,score_indicator_type,
                    comparison_direction,effective_score_rule,created_by
                ) VALUES (?,?,?,?,?,?,?,?)
                """, ruleVersionId, projectId, 1, "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", actorId);
        jdbc.update("""
                INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id)
                VALUES (?,?,?,?)
                """, activityProjectId, targetActivityId, projectId, ruleVersionId);
        return activityProjectId;
    }

    private void insertUser(UUID userId, String username) {
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, userId, username, "hash", "NORMAL");
    }

    private void insertParticipant(UUID participantId, UUID targetActivityId,
                                   UUID membershipId, Instant joinedAt) {
        jdbc.update("""
                INSERT INTO activity_participants(
                    id,activity_id,student_membership_id,created_at
                ) VALUES (?,?,?,?)
                """, participantId, targetActivityId, membershipId, ts(joinedAt));
    }

    private void insertProjectAssignment(UUID activityProjectId, UUID participantId) {
        jdbc.update("""
                INSERT INTO activity_project_participants(
                    id,activity_project_id,activity_participant_id,assigned_by,assigned_at
                ) VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), activityProjectId, participantId, actorId, ts(Instant.now()));
    }

    private void insertScoreAttempt(UUID activityProjectId, UUID studentId) {
        jdbc.update("""
                INSERT INTO score_attempts(
                    id,school_id,activity_project_id,student_id,attempt_number,
                    score_storage_type,score_value,is_current_effective,score_status,
                    entered_by,is_manual_makeup,created_at,updated_at,version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), schoolId, activityProjectId, studentId, 1,
                "INTEGER", 12, false, "DRAFT", actorId, false,
                ts(Instant.now()), ts(Instant.now()), 1);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record StudentFixture(UUID studentId, UUID membershipId) {}
}
