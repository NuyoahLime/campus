package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.port.TeacherResponsibleProjectQueryPort;
import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherResponsibleProjectQueryAdapterIT
        extends ScoreEntryIntegrationTestSupport {
    @Autowired TeacherResponsibleProjectQueryPort queries;

    private final List<UUID> responsibleAssignmentIds = new ArrayList<>();
    private UUID teacherMembershipId;

    @BeforeEach
    void assignBaseTeacher() {
        teacherMembershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='TEACHER'
                """, UUID.class, teacherId, schoolId);
        assignTeacher(activityProjectId, teacherMembershipId);
    }

    @AfterEach
    void removeTeacherAssignments() {
        for (UUID id : responsibleAssignmentIds.reversed()) {
            jdbc.update("DELETE FROM responsible_teachers WHERE id=?", id);
        }
    }

    @Test
    void teacherSeesOnlyAssignedProjects() {
        var otherProject = addProject(
                "Unassigned Project", "INTEGER", "pts", 0, null);
        addActivityProject(activityId, otherProject);

        var result = queries.findResponsibleProjects(
                teacherId, null, null, 0, 20);

        assertThat(result.items()).extracting("activityProjectId")
                .containsExactly(activityProjectId);
    }

    @Test
    void teacherCanSeeProjectsAcrossActiveSchoolMemberships() {
        UUID secondMembership = addMembership(
                teacherId, otherSchoolId, "TEACHER", "ACTIVE");
        UUID otherActivity = addActivity(
                otherSchoolId, "Other Campus Day", "PUBLISHED");
        UUID otherActivityProject = addActivityProject(
                otherActivity, baseProjectFixture());
        assignTeacher(otherActivityProject, secondMembership);

        var result = queries.findResponsibleProjects(
                teacherId, null, null, 0, 20);

        assertThat(result.items()).extracting("activityProjectId")
                .containsExactlyInAnyOrder(activityProjectId, otherActivityProject);
        assertThat(result.items()).extracting("schoolId")
                .containsExactlyInAnyOrder(schoolId, otherSchoolId);
    }

    @Test
    void inactiveTeacherMembershipIsExcluded() {
        jdbc.update(
                "UPDATE school_memberships SET status='ENDED' WHERE id=?",
                teacherMembershipId);

        assertThat(queries.findResponsibleProjects(
                teacherId, null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void unassignedProjectIsExcluded() {
        UUID otherActivity = addActivity(
                schoolId, "Unassigned Activity", "PUBLISHED");
        addActivityProject(otherActivity, baseProjectFixture());

        assertThat(queries.findResponsibleProjects(
                teacherId, null, "unassigned", 0, 20).items()).isEmpty();
    }

    @Test
    void keywordMatchesActivityTitleAndProjectName() {
        jdbc.update(
                "UPDATE activities SET title='Teacher Sprint Final' WHERE id=?",
                activityId);
        assertThat(queries.findResponsibleProjects(
                teacherId, null, " sprint ", 0, 20).items()).hasSize(1);

        jdbc.update(
                "UPDATE challenge_projects SET name='Teacher Precision Throw' WHERE id=?",
                projectId);
        assertThat(queries.findResponsibleProjects(
                teacherId, null, "precision", 0, 20).items()).hasSize(1);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        var secondProject = addProject(
                "Second Responsible Project", "INTEGER", "pts", 0, null);
        UUID secondActivityProject = addActivityProject(activityId, secondProject);
        assignTeacher(secondActivityProject, teacherMembershipId);

        var first = queries.findResponsibleProjects(
                teacherId, null, null, 0, 1);
        var second = queries.findResponsibleProjects(
                teacherId, null, null, 1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().activityProjectId())
                .isNotEqualTo(second.items().getFirst().activityProjectId());
    }

    @Test
    void projectCountsAreAccurate() {
        addAttempt(schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null,
                "PENDING_REVIEW");
        addAttempt(schoolId, activityProjectId, studentId, teacherId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null,
                "REJECTED");

        var item = queries.findResponsibleProjects(
                teacherId, null, null, 0, 20).items().getFirst();

        assertThat(item.participantCount()).isEqualTo(1);
        assertThat(item.enteredAttemptCount()).isEqualTo(2);
        assertThat(item.pendingReviewCount()).isEqualTo(1);
        assertThat(item.rejectedCount()).isEqualTo(1);
    }

    @Test
    void detailRequiresResponsibleAssignment() {
        assertThat(queries.findResponsibleProject(
                teacherId, activityProjectId)).isPresent();
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE activity_project_id=?",
                activityProjectId);

        assertThat(queries.findResponsibleProject(
                teacherId, activityProjectId)).isEmpty();
    }

    @Test
    void participantListReturnsAssignedStudentsOnly() {
        UUID extraStudent = addUser("activity-only-student");
        UUID extraMembership = addMembership(
                extraStudent, schoolId, "STUDENT", "ACTIVE");
        addParticipant(activityId, extraMembership);

        var result = queries.findProjectParticipants(
                teacherId, activityProjectId, null, null, 0, 20);

        assertThat(result.items()).extracting("studentId")
                .containsExactly(studentId);
    }

    @Test
    void participantKeywordMatchesUsername() {
        jdbc.update(
                "UPDATE users SET username='responsible-unique-student' WHERE id=?",
                studentId);

        var result = queries.findProjectParticipants(
                teacherId, activityProjectId, " UNIQUE ", null, 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("studentId").isEqualTo(studentId);
    }

    @Test
    void participantStatusFilterWorks() {
        assertThat(queries.findProjectParticipants(
                teacherId, activityProjectId, null, "NO_SCORE", 0, 20).items())
                .hasSize(1);
        addAttempt(schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null,
                "PENDING_REVIEW");

        assertThat(queries.findProjectParticipants(
                teacherId, activityProjectId, null, "NO_SCORE", 0, 20).items())
                .isEmpty();
        assertThat(queries.findProjectParticipants(
                teacherId, activityProjectId, null,
                "PENDING_REVIEW", 0, 20).items()).hasSize(1);
    }

    @Test
    void participantQueryDoesNotCreateNPlusOne() {
        UUID extraStudent = addUser("second-assigned-student");
        UUID extraMembership = addMembership(
                extraStudent, schoolId, "STUDENT", "ACTIVE");
        UUID extraParticipant = addParticipant(activityId, extraMembership);
        assignParticipant(activityProjectId, extraParticipant);

        var result = queries.findProjectParticipants(
                teacherId, activityProjectId, null, null, 0, 20);

        assertThat(result.items()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    private void assignTeacher(UUID targetProject, UUID membershipId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO responsible_teachers(
                  id,activity_project_id,teacher_membership_id,created_at)
                VALUES (?,?,?,?)
                """, id, targetProject, membershipId, ts(Instant.now()));
        responsibleAssignmentIds.add(id);
    }
}
