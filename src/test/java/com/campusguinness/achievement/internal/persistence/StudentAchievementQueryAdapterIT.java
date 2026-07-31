package com.campusguinness.achievement.internal.persistence;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import com.campusguinness.achievement.application.service.StudentAchievementApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAchievementQueryAdapterIT
        extends AchievementIntegrationTestSupport {

    @Autowired StudentAchievementApplicationService studentService;

    @Test
    void studentListsOwnRecordsOnly() {
        UUID secondStudent = addSecondStudent();
        var version = publishRanking();
        version.entries().forEach(entry ->
                achievementService.issue(adminId, entry.rankingEntryId()));

        var own = studentService.list(
                studentId, null, null, 0, 20);

        assertThat(own.items()).hasSize(1);
        assertThat(own.items().getFirst().recordTitle())
                .contains("Ranking Activity " + fixtureSuffix);
        assertThat(studentService.list(
                        secondStudent, null, null, 0, 20).items())
                .hasSize(1);
    }

    @Test
    void studentCannotReadOtherStudentRecord() {
        UUID secondStudent = addSecondStudent();
        var version = publishRanking();
        UUID secondEntry = version.entries().stream()
                .filter(entry -> entry.studentId().equals(secondStudent))
                .findFirst()
                .orElseThrow()
                .rankingEntryId();
        var otherRecord =
                achievementService.issue(adminId, secondEntry).record();

        assertThatThrownBy(() ->
                studentService.get(studentId, otherRecord.recordId()))
                .isInstanceOf(AchievementNotFoundException.class);
    }

    @Test
    void inactiveMembershipDoesNotHideHistoricalRecord() {
        var record = issueRecord(publishRanking());
        jdbc.update("""
                UPDATE school_memberships
                SET status='ENDED', ended_at=now()
                WHERE id=?
                """,
                studentMembershipId);

        assertThat(studentService.get(studentId, record.recordId()).recordId())
                .isEqualTo(record.recordId());
    }

    @Test
    void activeAndRevokedRecordsAreReturnedAndStatusFilterWorks() {
        var first = publishRanking();
        issueFirst(first);
        var second = publishRanking();
        issueFirst(second);
        rankingService.withdraw(adminId, activityProjectId, "ranking error");

        var all = studentService.list(
                studentId, null, null, 0, 20);
        var revoked = studentService.list(
                studentId, "REVOKED", null, 0, 20);
        var active = studentService.list(
                studentId, "ACTIVE", null, 0, 20);

        assertThat(all.items()).hasSize(2);
        assertThat(all.items()).extracting(item -> item.status())
                .containsExactlyInAnyOrder(
                        AchievementStatus.ACTIVE,
                        AchievementStatus.REVOKED);
        assertThat(revoked.items()).hasSize(1);
        assertThat(active.items()).hasSize(1);
    }

    @Test
    void keywordMatchesActivityAndProjectSnapshots() {
        issueFirst(publishRanking());

        assertThat(studentService.list(
                        studentId,
                        null,
                        "Activity " + fixtureSuffix,
                        0,
                        20)
                .items()).hasSize(1);
        assertThat(studentService.list(
                        studentId,
                        null,
                        "Project " + fixtureSuffix,
                        0,
                        20)
                .items()).hasSize(1);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        issueFirst(publishRanking());
        issueFirst(publishRanking());

        var page = studentService.list(
                studentId, null, null, 0, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void detailUsesImmutableSnapshots() {
        var version = publishRanking();
        var record = issueRecord(version);
        String originalSchool = "Ranking School " + fixtureSuffix;
        String originalActivity = "Ranking Activity " + fixtureSuffix;
        String originalProject = "Ranking Project " + fixtureSuffix;
        try {
            jdbc.update(
                    "UPDATE schools SET name='Changed School' WHERE id=?",
                    schoolId);
            jdbc.update(
                    "UPDATE activities SET title='Changed Activity' WHERE id=?",
                    activityId);
            jdbc.update(
                    "UPDATE challenge_projects SET name='Changed Project' WHERE id=?",
                    projectId);

            var detail = studentService.get(studentId, record.recordId());

            assertThat(detail.schoolName()).isEqualTo(originalSchool);
            assertThat(detail.activityTitle()).isEqualTo(originalActivity);
            assertThat(detail.projectName()).isEqualTo(originalProject);
            assertThat(detail.rankPosition()).isEqualTo(1);
            assertThat(detail.scoreDisplayValue()).isEqualTo("100");
        } finally {
            jdbc.update(
                    "UPDATE schools SET name=? WHERE id=?",
                    originalSchool,
                    schoolId);
            jdbc.update(
                    "UPDATE activities SET title=? WHERE id=?",
                    originalActivity,
                    activityId);
            jdbc.update(
                    "UPDATE challenge_projects SET name=? WHERE id=?",
                    originalProject,
                    projectId);
        }
    }

    @Test
    void detailReturnsRevocationReason() {
        var version = publishRanking();
        var record = issueRecord(version);
        rankingService.withdraw(adminId, activityProjectId, "ranking error");

        var detail = studentService.get(studentId, record.recordId());

        assertThat(detail.status()).isEqualTo(AchievementStatus.REVOKED);
        assertThat(detail.revocationReason()).isEqualTo("ranking error");
    }

    private UUID addSecondStudent() {
        UUID secondStudent = createUser(
                "ranking-query-student-" + fixtureSuffix);
        UUID secondMembership = membership(
                secondStudent, schoolId, "STUDENT", "ACTIVE");
        assignStudent(
                activityId,
                activityProjectId,
                secondMembership,
                adminId);
        createScore(
                activityProjectId,
                schoolId,
                secondStudent,
                teacherId,
                "INTEGER",
                new BigDecimal("90"),
                null,
                null,
                "APPROVED",
                true,
                Instant.parse("2026-07-30T09:00:00Z"));
        return secondStudent;
    }
}
