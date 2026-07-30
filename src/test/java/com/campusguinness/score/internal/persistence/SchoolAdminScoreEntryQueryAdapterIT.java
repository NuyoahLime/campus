package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import com.campusguinness.score.application.query.port.SchoolAdminScoreEntryQueryPort;
import com.campusguinness.score.application.query.port.SchoolAdminScoreQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolAdminScoreEntryQueryAdapterIT extends ScoreEntryIntegrationTestSupport {
    @Autowired SchoolAdminScoreEntryQueryPort entryQueries;
    @Autowired SchoolAdminScoreQueryPort scoreQueries;

    @Test
    void projectOptionsReturnOwnSchoolOnly() {
        var otherActivity = addActivity(otherSchoolId, "Other School Activity", "PUBLISHED");
        addActivityProject(otherActivity, baseProjectFixture());

        var result = entryQueries.findProjectOptions(schoolId, null, 0, 20);

        assertThat(result.items()).extracting("activityProjectId")
                .contains(activityProjectId);
        assertThat(result.items()).extracting("activityId")
                .doesNotContain(otherActivity);
    }

    @Test
    void terminalActivitiesExcludedFromProjectOptions() {
        jdbc.update("UPDATE activities SET execution_status='ENDED' WHERE id=?", activityId);
        assertThat(entryQueries.findProjectOptions(schoolId, null, 0, 20).items())
                .isEmpty();

        jdbc.update("UPDATE activities SET execution_status='CANCELLED' WHERE id=?", activityId);
        assertThat(entryQueries.findProjectOptions(schoolId, null, 0, 20).items())
                .isEmpty();
    }

    @Test
    void projectKeywordMatchesActivityTitle() {
        jdbc.update("UPDATE activities SET title='Campus Sprint Final' WHERE id=?", activityId);

        var result = entryQueries.findProjectOptions(schoolId, " sprint ", 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("activityProjectId").isEqualTo(activityProjectId);
    }

    @Test
    void projectKeywordMatchesProjectName() {
        jdbc.update("UPDATE challenge_projects SET name='Precision Throw' WHERE id=?", projectId);

        var result = entryQueries.findProjectOptions(schoolId, "precision", 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("activityProjectId").isEqualTo(activityProjectId);
    }

    @Test
    void projectOptionPaginationReturnsCorrectTotal() {
        ProjectFixture project = addProject("Second Entry Project", "INTEGER", "pts", 0, null);
        addActivityProject(activityId, project);

        var first = entryQueries.findProjectOptions(schoolId, null, 0, 1);
        var second = entryQueries.findProjectOptions(schoolId, null, 1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().activityProjectId())
                .isNotEqualTo(second.items().getFirst().activityProjectId());
    }

    @Test
    void participantOptionsReturnAssignedStudentsOnly() {
        var result = entryQueries.findParticipantOptions(
                schoolId, activityProjectId, null, 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("studentId").isEqualTo(studentId);
    }

    @Test
    void participantOptionsExcludeOtherProjectStudents() {
        ProjectFixture project = addProject("Other Assigned Project", "INTEGER", "pts", 0, null);
        var otherActivityProject = addActivityProject(activityId, project);
        var extraStudent = addUser("other-project-student");
        var extraMembership = addMembership(extraStudent, schoolId, "STUDENT", "ACTIVE");
        var extraParticipant = addParticipant(activityId, extraMembership);
        assignParticipant(otherActivityProject, extraParticipant);

        var result = entryQueries.findParticipantOptions(
                schoolId, activityProjectId, null, 0, 20);

        assertThat(result.items()).extracting("studentId")
                .containsExactly(studentId);
    }

    @Test
    void participantKeywordMatchesUsername() {
        jdbc.update("UPDATE users SET username='entry-unique-student' WHERE id=?", studentId);

        var result = entryQueries.findParticipantOptions(
                schoolId, activityProjectId, " UNIQUE ", 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("studentId").isEqualTo(studentId);
    }

    @Test
    void participantKeywordMatchesStudentNumber() {
        var membershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='STUDENT'
                """, java.util.UUID.class, studentId, schoolId);
        jdbc.update("""
                INSERT INTO student_profiles(
                  membership_id,student_number,grade,class_name)
                VALUES (?,?,?,?)
                """, membershipId, "SN-ENTRY-9001", "6", "Class 1");

        var result = entryQueries.findParticipantOptions(
                schoolId, activityProjectId, "9001", 0, 20);

        assertThat(result.items()).singleElement()
                .extracting("studentNumber").isEqualTo("SN-ENTRY-9001");
    }

    @Test
    void participantOptionReturnsAttemptSummary() {
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                2, "INTEGER", BigDecimal.valueOf(95), null, null, "PENDING_REVIEW");

        var result = entryQueries.findParticipantOptions(
                schoolId, activityProjectId, null, 0, 20);

        assertThat(result.items()).singleElement().satisfies(option -> {
            assertThat(option.attemptCount()).isEqualTo(2);
            assertThat(option.latestAttemptNumber()).isEqualTo(2);
            assertThat(option.latestAttemptStatus()).isEqualTo("PENDING_REVIEW");
            assertThat(option.latestScoreValue()).isEqualTo("95");
        });
    }

    @Test
    void mineReturnsOnlyCurrentActorEntries() {
        var own = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, entrantId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "DRAFT");

        var result = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, null, null, null, null, 0, 20);

        assertThat(result.items()).extracting("attemptId").containsExactly(own);
        assertThat(result.items()).allMatch(item -> item.enteredBy().equals(adminId));
    }

    @Test
    void mineStatusFilterWorks() {
        var draft = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "REJECTED");

        var result = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, "DRAFT", null, null, null, 0, 20);

        assertThat(result.items()).extracting("attemptId").containsExactly(draft);
    }

    @Test
    void mineReturnsDraftRejectedAndPending() {
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "REJECTED");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                3, "INTEGER", BigDecimal.valueOf(100), null, null, "PENDING_REVIEW");

        var result = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, null, null, null, null, 0, 20);

        assertThat(result.items()).extracting("status")
                .containsExactlyInAnyOrder("DRAFT", "REJECTED", "PENDING_REVIEW");
    }

    @Test
    void mineExcludesOtherSchoolScores() {
        var otherActivity = addActivity(otherSchoolId, "Other School Score", "PUBLISHED");
        var otherActivityProject = addActivityProject(
                otherActivity, baseProjectFixture());
        addAttempt(otherSchoolId, otherActivityProject, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(70), null, null, "DRAFT");
        var own = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        var result = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, null, null, null, null, 0, 20);

        assertThat(result.items()).extracting("attemptId").containsExactly(own);
    }

    @Test
    void minePaginationIsStable() {
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "REJECTED");
        addAttempt(schoolId, activityProjectId, studentId, adminId,
                3, "INTEGER", BigDecimal.valueOf(100), null, null, "PENDING_REVIEW");

        var first = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, null, null, null, null, 0, 2);
        var second = scoreQueries.findEnteredBySchoolAdmin(
                schoolId, adminId, null, null, null, null, 1, 2);

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        var firstIds = new HashSet<>(
                first.items().stream().map(item -> item.attemptId()).toList());
        assertThat(second.items()).noneMatch(item -> firstIds.contains(item.attemptId()));
    }
}
