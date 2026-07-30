package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.query.model.RankingStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;
import com.campusguinness.ranking.application.query.port.RankingScoreSourceQueryPort;
import com.campusguinness.ranking.application.query.port.SchoolAdminRankingQueryPort;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolAdminRankingQueryAdapterIT extends RankingIntegrationTestSupport {

    @Autowired SchoolAdminRankingQueryPort query;
    @Autowired RankingScoreSourceQueryPort sourceQuery;
    @Autowired SchoolAdminRankingApplicationService service;

    @Test
    void projectListReturnsOwnSchoolOnly() {
        var page = query.findProjects(schoolId, null, null, null, 0, 20);

        assertThat(page.items()).extracting(item -> item.activityProjectId())
                .contains(activityProjectId);
        assertThat(query.findProjects(
                otherSchoolId, null, null, null, 0, 20).items())
                .extracting(item -> item.activityProjectId())
                .doesNotContain(activityProjectId);
    }

    @Test
    void executionStatusFilterWorks() {
        assertThat(query.findProjects(
                schoolId, "ENDED", null, null, 0, 20).items()).hasSize(1);
        assertThat(query.findProjects(
                schoolId, "IN_PROGRESS", null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void rankingStatusFilterWorks() {
        publish();

        var page = query.findProjects(
                schoolId, null, "CURRENT", null, 0, 20);

        assertThat(page.items()).singleElement()
                .extracting(item -> item.rankingStatus())
                .isEqualTo(RankingStatus.CURRENT);
    }

    @Test
    void keywordMatchesActivityTitle() {
        var page = query.findProjects(
                schoolId, null, null, "Ranking Activity", 0, 20);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void keywordMatchesProjectName() {
        var page = query.findProjects(
                schoolId, null, null, "Ranking Project", 0, 20);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        var page = query.findProjects(
                schoolId, null, null, null, 0, 1);

        assertThat(page.totalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void projectCountsCurrentEffectiveScores() {
        var item = query.findProjects(
                schoolId, null, null, null, 0, 20).items().getFirst();

        assertThat(item.approvedEffectiveScoreCount()).isEqualTo(1);
    }

    @Test
    void projectCountsPendingReviewScores() {
        createScore(
                activityProjectId,
                schoolId,
                studentId,
                teacherId,
                "INTEGER",
                BigDecimal.valueOf(80),
                null,
                null,
                "PENDING_REVIEW",
                false,
                Instant.parse("2026-07-30T09:00:00Z"));

        var item = query.findProjects(
                schoolId, null, null, null, 0, 20).items().getFirst();

        assertThat(item.pendingReviewCount()).isEqualTo(1);
    }

    @Test
    void noRankingProjectIsDisabled() {
        jdbc.update("""
                UPDATE challenge_projects
                SET comparison_direction='NO_RANKING'
                WHERE id=?
                """, projectId);

        var item = query.findProject(schoolId, activityProjectId).orElseThrow();

        assertThat(item.rankingStatus()).isEqualTo(RankingStatus.DISABLED);
        assertThat(item.canPreview()).isFalse();
        assertThat(item.canPublish()).isFalse();
    }

    @Test
    void currentVersionMetadataIsReturned() {
        var published = publish();

        var item = query.findProject(schoolId, activityProjectId).orElseThrow();

        assertThat(item.currentVersionId()).isEqualTo(published.versionId());
        assertThat(item.currentVersionNumber()).isEqualTo(1);
        assertThat(item.currentVersionEntryCount()).isEqualTo(1);
    }

    @Test
    void previewSourceExcludesNonEffectiveApprovedScore() {
        createScore(
                activityProjectId,
                schoolId,
                studentId,
                teacherId,
                "INTEGER",
                BigDecimal.valueOf(999),
                null,
                null,
                "APPROVED",
                false,
                Instant.parse("2026-07-30T09:00:00Z"));

        var sources = sourceQuery.findCurrentEffectiveApprovedSources(
                schoolId, activityProjectId);

        assertThat(sources).singleElement()
                .extracting(source -> source.scoreAttemptId())
                .isEqualTo(scoreAttemptId);
    }

    @Test
    void previewSourceExcludesPendingScore() {
        createScore(
                activityProjectId,
                schoolId,
                studentId,
                teacherId,
                "INTEGER",
                BigDecimal.valueOf(999),
                null,
                null,
                "PENDING_REVIEW",
                false,
                Instant.parse("2026-07-30T09:00:00Z"));

        assertThat(sourceQuery.findCurrentEffectiveApprovedSources(
                schoolId, activityProjectId)).hasSize(1);
    }

    @Test
    void previewSourceIncludesAssignedActiveStudent() {
        assertThat(sourceQuery.findCurrentEffectiveApprovedSources(
                schoolId, activityProjectId)).singleElement()
                .extracting(source -> source.studentId())
                .isEqualTo(studentId);
    }

    @Test
    void previewSourceExcludesRemovedAssignment() {
        jdbc.update(
                "DELETE FROM activity_project_participants WHERE activity_project_id=?",
                activityProjectId);

        assertThat(sourceQuery.findCurrentEffectiveApprovedSources(
                schoolId, activityProjectId)).isEmpty();
    }

    @Test
    void duplicateRowsAreNotProduced() {
        assertThat(sourceQuery.findCurrentEffectiveApprovedSources(
                schoolId, activityProjectId))
                .extracting(source -> source.studentId())
                .doesNotHaveDuplicates();
    }

    @Test
    void versionHistoryIsStable() {
        var first = publish();
        var second = publish();

        var history = query.findVersions(
                schoolId, activityProjectId, 0, 20);

        assertThat(history.items()).extracting(item -> item.versionId())
                .containsExactly(second.versionId(), first.versionId());
        assertThat(history.items()).extracting(item -> item.versionStatus())
                .containsExactly(
                        RankingVersionStatus.PUBLISHED,
                        RankingVersionStatus.REPLACED);
    }

    @Test
    void versionDetailUsesSnapshotEntries() {
        var published = publish();
        jdbc.update(
                "UPDATE score_attempts SET score_value=777 WHERE id=?",
                scoreAttemptId);

        var detail = query.findVersion(
                schoolId, published.versionId()).orElseThrow();

        assertThat(detail.entries()).singleElement()
                .extracting(entry -> entry.scoreDisplayValue())
                .isEqualTo("100");
    }

    @Test
    void versionDetailReturnsSourceAttemptId() {
        var published = publish();

        var detail = query.findVersion(
                schoolId, published.versionId()).orElseThrow();

        assertThat(detail.entries()).singleElement()
                .extracting(entry -> entry.scoreAttemptId())
                .isEqualTo(scoreAttemptId);
    }

    @Test
    void otherSchoolVersionLooksNotFound() {
        var published = publish();

        assertThat(query.findVersion(
                otherSchoolId, published.versionId())).isEmpty();
    }

    private com.campusguinness.ranking.application.query.model.RankingVersionDetail publish() {
        String fingerprint = service.preview(adminId, activityProjectId)
                .sourceFingerprint();
        return service.publish(adminId, activityProjectId, fingerprint);
    }
}
