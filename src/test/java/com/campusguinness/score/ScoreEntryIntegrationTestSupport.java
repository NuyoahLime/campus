package com.campusguinness.score;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ScoreEntryIntegrationTestSupport
        extends ScoreReviewIntegrationTestSupport {
    protected UUID activityParticipantId;
    protected UUID projectParticipantId;

    private final List<UUID> extraAssignmentIds = new ArrayList<>();
    private final List<UUID> extraParticipantIds = new ArrayList<>();
    private final List<UUID> extraActivityProjectIds = new ArrayList<>();
    private final List<UUID> extraActivityIds = new ArrayList<>();
    private final List<UUID> extraProjectIds = new ArrayList<>();
    private final List<UUID> extraUserIds = new ArrayList<>();
    private final List<UUID> extraMembershipIds = new ArrayList<>();

    @BeforeEach
    void seedScoreEntryFixture() {
        jdbc.update("DELETE FROM score_attempts WHERE id=?", attemptId);
        UUID membershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='STUDENT'
                """, UUID.class, studentId, schoolId);
        activityParticipantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_participants(
                  id,activity_id,student_membership_id,created_at)
                VALUES (?,?,?,?)
                """, activityParticipantId, activityId, membershipId, ts(Instant.now()));
        projectParticipantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_project_participants(
                  id,activity_project_id,activity_participant_id,assigned_by,assigned_at)
                VALUES (?,?,?,?,?)
                """, projectParticipantId, activityProjectId,
                activityParticipantId, adminId, ts(Instant.now()));
    }

    @AfterEach
    void cleanScoreEntryFixture() {
        jdbc.update("""
                DELETE FROM score_review_records
                WHERE score_attempt_id IN (
                  SELECT id FROM score_attempts WHERE school_id IN (?,?))
                """, schoolId, otherSchoolId);
        jdbc.update("DELETE FROM score_attempts WHERE school_id IN (?,?)",
                schoolId, otherSchoolId);
        for (UUID id : extraAssignmentIds.reversed()) {
            jdbc.update("DELETE FROM activity_project_participants WHERE id=?", id);
        }
        jdbc.update("DELETE FROM activity_project_participants WHERE id=?",
                projectParticipantId);
        for (UUID id : extraParticipantIds.reversed()) {
            jdbc.update("DELETE FROM activity_participants WHERE id=?", id);
        }
        jdbc.update("DELETE FROM activity_participants WHERE id=?", activityParticipantId);
        for (UUID id : extraActivityProjectIds.reversed()) {
            jdbc.update("DELETE FROM activity_projects WHERE id=?", id);
        }
        for (UUID id : extraActivityIds.reversed()) {
            jdbc.update("DELETE FROM activities WHERE id=?", id);
        }
        for (UUID id : extraProjectIds.reversed()) {
            jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", id);
            jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", id);
            jdbc.update("DELETE FROM challenge_projects WHERE id=?", id);
        }
        jdbc.update("""
                DELETE FROM student_profiles
                WHERE membership_id IN (
                  SELECT id FROM school_memberships WHERE school_id IN (?,?))
                """, schoolId, otherSchoolId);
        for (UUID id : extraMembershipIds.reversed()) {
            jdbc.update("DELETE FROM school_memberships WHERE id=?", id);
        }
        for (UUID id : extraUserIds.reversed()) {
            jdbc.update("DELETE FROM users WHERE id=?", id);
        }
    }

    protected UUID addUser(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, id, prefix + "-" + id.toString().substring(0, 8),
                "$2a$10$hash0000000000000000000000", "NORMAL");
        extraUserIds.add(id);
        return id;
    }

    protected UUID addMembership(
            UUID userId, UUID membershipSchoolId, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(
                  id,user_id,school_id,role_in_school,status,started_at,created_at,version)
                VALUES (?,?,?,?,?,now(),now(),1)
                """, id, userId, membershipSchoolId, role, status);
        extraMembershipIds.add(id);
        return id;
    }

    protected UUID addActivity(
            UUID activitySchoolId, String title, String executionStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                  id,school_id,title,execution_status,public_status,created_by,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, activitySchoolId, title, executionStatus,
                "NOT_SUBMITTED", adminId, ts(Instant.now()), ts(Instant.now()), 1);
        extraActivityIds.add(id);
        return id;
    }

    protected ProjectFixture addProject(
            String name,
            String storageType,
            String scoreUnit,
            Integer decimalPlaces,
            String gradeOrder) {
        UUID addedProjectId = UUID.randomUUID();
        UUID addedRuleVersionId = UUID.randomUUID();
        String indicatorType = switch (storageType) {
            case "DURATION" -> "DURATION_MS";
            case "GRADE" -> "GRADE_LEVEL";
            default -> "NUMERIC";
        };
        String comparison = switch (storageType) {
            case "DURATION" -> "LOWER_BETTER";
            case "GRADE" -> "GRADE_ORDER";
            default -> "HIGHER_BETTER";
        };
        jdbc.update("""
                INSERT INTO challenge_projects(
                  id,name,category,score_storage_type,score_indicator_type,
                  comparison_direction,score_unit,decimal_places,grade_order,allow_tie,
                  effective_score_rule,project_status,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, addedProjectId, name, "SPORT", storageType, indicatorType,
                comparison, scoreUnit, decimalPlaces, gradeOrder, true,
                "BEST", "PUBLISHED", ts(Instant.now()), ts(Instant.now()), 1);
        jdbc.update("""
                INSERT INTO project_rule_versions(
                  id,project_id,version_number,score_storage_type,score_indicator_type,
                  comparison_direction,effective_score_rule,score_unit,decimal_places,
                  grade_order,created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, addedRuleVersionId, addedProjectId, 1, storageType, indicatorType,
                comparison, "BEST", scoreUnit, decimalPlaces, gradeOrder,
                adminId, ts(Instant.now()));
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?",
                addedRuleVersionId, addedProjectId);
        extraProjectIds.add(addedProjectId);
        return new ProjectFixture(addedProjectId, addedRuleVersionId);
    }

    protected UUID addActivityProject(
            UUID targetActivityId, ProjectFixture project) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_projects(
                  id,activity_id,project_id,rule_version_id,created_at)
                VALUES (?,?,?,?,?)
                """, id, targetActivityId, project.projectId(),
                project.ruleVersionId(), ts(Instant.now()));
        extraActivityProjectIds.add(id);
        return id;
    }

    protected ProjectFixture baseProjectFixture() {
        return new ProjectFixture(projectId, ruleVersionId);
    }

    protected UUID addParticipant(UUID targetActivityId, UUID membershipId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_participants(
                  id,activity_id,student_membership_id,created_at)
                VALUES (?,?,?,?)
                """, id, targetActivityId, membershipId, ts(Instant.now()));
        extraParticipantIds.add(id);
        return id;
    }

    protected UUID assignParticipant(
            UUID targetActivityProjectId, UUID participantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_project_participants(
                  id,activity_project_id,activity_participant_id,assigned_by,assigned_at)
                VALUES (?,?,?,?,?)
                """, id, targetActivityProjectId, participantId,
                adminId, ts(Instant.now()));
        extraAssignmentIds.add(id);
        return id;
    }

    protected UUID addAttempt(
            UUID targetSchoolId,
            UUID targetActivityProjectId,
            UUID targetStudentId,
            UUID targetEntrantId,
            int attemptNumber,
            String storageType,
            BigDecimal numericValue,
            Long durationMs,
            String grade,
            String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO score_attempts(
                  id,school_id,activity_project_id,student_id,attempt_number,
                  score_storage_type,score_value,score_duration_ms,score_grade,
                  score_business_time,time_source,is_current_effective,
                  score_status,entered_by,submitted_at,is_manual_makeup,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, targetSchoolId, targetActivityProjectId, targetStudentId,
                attemptNumber, storageType, numericValue, durationMs, grade,
                ts(now), "ON_SITE_RECORD", false, status, targetEntrantId,
                "DRAFT".equals(status) ? null : ts(now), false, ts(now), ts(now), 1);
        return id;
    }

    protected record ProjectFixture(UUID projectId, UUID ruleVersionId) {
    }
}
