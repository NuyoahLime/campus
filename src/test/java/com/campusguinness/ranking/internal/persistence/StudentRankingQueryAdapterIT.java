package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.query.model.StudentRankingAvailability;
import com.campusguinness.ranking.application.query.port.StudentRankingQueryPort;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRankingQueryAdapterIT extends RankingIntegrationTestSupport {

    @Autowired StudentRankingQueryPort query;
    @Autowired SchoolAdminRankingApplicationService managementService;

    @Test
    void listUsesActiveStudentMembership() {
        assertThat(list(studentId).items()).hasSize(1);

        jdbc.update(
                "UPDATE school_memberships SET status='ENDED' WHERE id=?",
                studentMembershipId);

        assertThat(list(studentId).items()).isEmpty();
    }

    @Test
    void listJoinsCorrectStudentMembershipId() {
        membership(studentId, otherSchoolId, "STUDENT", "ACTIVE");
        jdbc.update(
                "UPDATE school_memberships SET status='ENDED' WHERE id=?",
                studentMembershipId);

        assertThat(list(studentId).items()).isEmpty();
    }

    @Test
    void multiSchoolMembershipDoesNotLeakProjects() {
        UUID otherStudent = createUser(
                "ranking-other-student-" + fixtureSuffix);
        UUID otherMembership = membership(
                otherStudent, otherSchoolId, "STUDENT", "ACTIVE");
        createAssignedProject(
                otherSchoolId, otherAdminId, otherMembership, "Other");

        assertThat(list(studentId).items())
                .extracting(item -> item.activityProjectId())
                .containsExactly(activityProjectId);
    }

    @Test
    void sameProjectIsNotDuplicated() {
        assertThat(list(studentId).items())
                .extracting(item -> item.activityProjectId())
                .containsExactly(activityProjectId);
    }

    @Test
    void executionStatusFilterWorks() {
        assertThat(query.findRankingProjects(
                studentId, "ENDED", null, null, 0, 20).items()).hasSize(1);
        assertThat(query.findRankingProjects(
                studentId, "IN_PROGRESS", null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void rankingAvailabilityFilterWorks() {
        publish();

        assertThat(query.findRankingProjects(
                studentId, null, "CURRENT", null, 0, 20).items())
                .singleElement()
                .extracting(item -> item.rankingAvailability())
                .isEqualTo(StudentRankingAvailability.CURRENT);
        assertThat(query.findRankingProjects(
                studentId, null, "WITHDRAWN", null, 0, 20).items()).isEmpty();
    }

    @Test
    void keywordMatchesSchoolName() {
        assertThat(query.findRankingProjects(
                studentId, null, null, "Ranking School", 0, 20).items())
                .hasSize(1);
    }

    @Test
    void keywordMatchesActivityTitle() {
        assertThat(query.findRankingProjects(
                studentId, null, null, "Ranking Activity", 0, 20).items())
                .hasSize(1);
    }

    @Test
    void keywordMatchesProjectName() {
        assertThat(query.findRankingProjects(
                studentId, null, null, "Ranking Project", 0, 20).items())
                .hasSize(1);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        createAssignedProject(
                schoolId, adminId, studentMembershipId, "Second");

        var first = query.findRankingProjects(
                studentId, null, null, null, 0, 1);
        var second = query.findRankingProjects(
                studentId, null, null, null, 1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
    }

    @Test
    void currentVersionMetadataIsReturned() {
        var published = publish();

        var item = list(studentId).items().getFirst();

        assertThat(item.currentVersionNumber())
                .isEqualTo(published.versionNumber());
        assertThat(item.publishedAt()).isNotNull();
        assertThat(item.totalRanked()).isEqualTo(1);
    }

    @Test
    void ownRankIsReturned() {
        publish();

        var item = list(studentId).items().getFirst();

        assertThat(item.myRank()).isEqualTo(1);
        assertThat(item.myScoreDisplayValue()).isEqualTo("100");
    }

    @Test
    void studentWithoutRankingEntryHasNullRank() {
        UUID unranked = createUser("ranking-unranked-" + fixtureSuffix);
        UUID membership = membership(
                unranked, schoolId, "STUDENT", "ACTIVE");
        assignStudent(activityId, activityProjectId, membership, adminId);
        publish();

        var item = list(unranked).items().getFirst();

        assertThat(item.rankingAvailability())
                .isEqualTo(StudentRankingAvailability.CURRENT);
        assertThat(item.myRank()).isNull();
        assertThat(item.myScoreDisplayValue()).isNull();
    }

    @Test
    void withdrawnVersionIsNotCurrent() {
        publish();
        managementService.withdraw(
                adminId, activityProjectId, "withdrawn");

        var item = list(studentId).items().getFirst();

        assertThat(item.rankingAvailability())
                .isEqualTo(StudentRankingAvailability.WITHDRAWN);
        assertThat(query.findAccessibleCurrentRanking(
                studentId, activityProjectId)).isEmpty();
    }

    @Test
    void replacedVersionIsNotCurrent() {
        var first = publish();
        var second = publish();

        var detail = query.findAccessibleCurrentRanking(
                studentId, activityProjectId).orElseThrow();

        assertThat(detail.versionNumber()).isEqualTo(second.versionNumber());
        assertThat(detail.versionNumber()).isNotEqualTo(first.versionNumber());
    }

    @Test
    void detailPreservesPublishedEntryOrder() {
        UUID secondStudent = createUser(
                "ranking-second-student-" + fixtureSuffix);
        UUID secondMembership = membership(
                secondStudent, schoolId, "STUDENT", "ACTIVE");
        assignStudent(
                activityId, activityProjectId, secondMembership, adminId);
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
                Instant.parse("2026-07-30T08:01:00Z"));
        publish();

        assertThat(query.findAccessibleCurrentRanking(
                studentId, activityProjectId).orElseThrow().entries())
                .extracting(entry -> entry.rankPosition())
                .containsExactly(1, 2);
    }

    @Test
    void detailUsesSnapshotDisplayName() {
        publish();
        jdbc.update("""
                UPDATE ranking_entries
                SET student_display_name='Snapshot Name'
                WHERE student_id=?
                """, studentId);
        jdbc.update(
                "UPDATE users SET username='live-" + fixtureSuffix + "' WHERE id=?",
                studentId);

        assertThat(query.findAccessibleCurrentRanking(
                studentId, activityProjectId).orElseThrow().entries())
                .singleElement()
                .extracting(entry -> entry.studentDisplayName())
                .isEqualTo("Snapshot Name");
    }

    @Test
    void detailDoesNotReadLiveScore() {
        publish();
        jdbc.update(
                "UPDATE score_attempts SET score_value=999 WHERE id=?",
                scoreAttemptId);

        assertThat(query.findAccessibleCurrentRanking(
                studentId, activityProjectId).orElseThrow().entries())
                .singleElement()
                .extracting(entry -> entry.scoreDisplayValue())
                .isEqualTo("100");
    }

    @Test
    void queryDoesNotCreateNPlusOne() {
        createAssignedProject(
                schoolId, adminId, studentMembershipId, "Second");
        createAssignedProject(
                schoolId, adminId, studentMembershipId, "Third");

        var page = list(studentId);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(3);
    }

    private com.campusguinness.project.application.query.model.QueryPage<
            com.campusguinness.ranking.application.query.model.StudentRankingProjectItem>
            list(UUID actorId) {
        return query.findRankingProjects(
                actorId, null, null, null, 0, 20);
    }

    private com.campusguinness.ranking.application.query.model.RankingVersionDetail
            publish() {
        String fingerprint = managementService.preview(
                adminId, activityProjectId).sourceFingerprint();
        return managementService.publish(
                adminId, activityProjectId, fingerprint);
    }

    private UUID createAssignedProject(
            UUID school,
            UUID creator,
            UUID studentMembership,
            String label) {
        UUID project = createProject(
                "Ranking Project " + fixtureSuffix + " " + label,
                "INTEGER",
                "HIGHER_BETTER",
                "BEST",
                null,
                true,
                0,
                creator);
        UUID rule = jdbc.queryForObject(
                "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                UUID.class,
                project);
        UUID activity = createActivity(
                school,
                creator,
                "Ranking Activity " + fixtureSuffix + " " + label,
                "ENDED");
        UUID activityProject = attachProject(activity, project, rule);
        assignStudent(
                activity, activityProject, studentMembership, creator);
        return activityProject;
    }
}
