package com.campusguinness.ranking.application.service;

import com.campusguinness.achievement.application.service.AchievementRecordService;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.ranking.application.exception.RankingConflictException;
import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.exception.RankingSourceChangedException;
import com.campusguinness.ranking.application.port.RankingDefinitionPort;
import com.campusguinness.ranking.application.port.RankingPublicationPort;
import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;
import com.campusguinness.ranking.application.query.model.RankingStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;
import com.campusguinness.ranking.application.query.model.TiePolicy;
import com.campusguinness.ranking.application.query.port.RankingScoreSourceQueryPort;
import com.campusguinness.ranking.application.query.port.SchoolAdminRankingQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminRankingApplicationServiceTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID SCHOOL = UUID.randomUUID();
    private static final UUID ACTIVITY = UUID.randomUUID();
    private static final UUID ACTIVITY_PROJECT = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID RULE = UUID.randomUUID();
    private static final UUID DEFINITION = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();

    @Mock SchoolMembershipQueryPort membershipQuery;
    @Mock SchoolAdminRankingQueryPort rankingQuery;
    @Mock RankingScoreSourceQueryPort sourceQuery;
    @Mock RankingDefinitionPort definitionPort;
    @Mock RankingPublicationPort publicationPort;
    @Mock AchievementRecordService achievementService;

    private SchoolAdminRankingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SchoolAdminRankingApplicationService(
                membershipQuery,
                rankingQuery,
                sourceQuery,
                definitionPort,
                publicationPort,
                achievementService);
        when(membershipQuery.findActiveSchoolAdminSchoolId(ACTOR))
                .thenReturn(Optional.of(SCHOOL));
    }

    @Test
    void schoolAdminCanPreviewOwnSchoolProject() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 0, 1, null));

        var result = service.preview(ACTOR, ACTIVITY_PROJECT);

        assertThat(result.totalRanked()).isEqualTo(1);
        assertThat(result.publishable()).isTrue();
    }

    @Test
    void otherSchoolProjectLooksNotFound() {
        when(rankingQuery.findProject(SCHOOL, ACTIVITY_PROJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(ACTOR, ACTIVITY_PROJECT))
                .isInstanceOf(RankingNotFoundException.class);
    }

    @Test
    void inactiveSchoolAdminCannotPreview() {
        when(membershipQuery.findActiveSchoolAdminSchoolId(ACTOR))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(ACTOR, ACTIVITY_PROJECT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void inProgressProjectCanPreview() {
        arrangeProject(project("IN_PROGRESS", "HIGHER_BETTER", 0, 1, null));

        var result = service.preview(ACTOR, ACTIVITY_PROJECT);

        assertThat(result.publishable()).isFalse();
        assertThat(result.warnings()).contains("活动尚未结束");
    }

    @Test
    void endedProjectCanPreview() {
        arrangeProject(project("ENDED", "LOWER_BETTER", 0, 1, null));

        assertThat(service.preview(ACTOR, ACTIVITY_PROJECT).entries()).hasSize(1);
    }

    @Test
    void draftProjectCannotPreview() {
        arrangeProject(project("DRAFT", "HIGHER_BETTER", 0, 1, null));

        assertConflict(() -> service.preview(ACTOR, ACTIVITY_PROJECT),
                "RANKING_PREVIEW_NOT_ALLOWED");
    }

    @Test
    void cancelledProjectCannotPreview() {
        arrangeProject(project("CANCELLED", "HIGHER_BETTER", 0, 1, null));

        assertConflict(() -> service.preview(ACTOR, ACTIVITY_PROJECT),
                "RANKING_PREVIEW_NOT_ALLOWED");
    }

    @Test
    void noRankingProjectCannotPreview() {
        arrangeProject(project("ENDED", "NO_RANKING", 0, 1, null));

        assertConflict(() -> service.preview(ACTOR, ACTIVITY_PROJECT),
                "RANKING_DISABLED_FOR_PROJECT");
    }

    @Test
    void previewUsesOnlyCurrentEffectiveApprovedScores() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 0, 1, null));

        service.preview(ACTOR, ACTIVITY_PROJECT);

        verify(sourceQuery).findCurrentEffectiveApprovedSources(
                SCHOOL, ACTIVITY_PROJECT);
    }

    @Test
    void approvedHistoricalScoreIsExcluded() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 0, 1, null));
        when(sourceQuery.findCurrentEffectiveApprovedSources(
                SCHOOL, ACTIVITY_PROJECT)).thenReturn(List.of(source(90)));

        var result = service.preview(ACTOR, ACTIVITY_PROJECT);

        assertThat(result.entries()).singleElement()
                .extracting(entry -> entry.scoreDisplayValue())
                .isEqualTo("90");
    }

    @Test
    void pendingScoreIsExcludedFromPreview() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 2, 1, null));

        var result = service.preview(ACTOR, ACTIVITY_PROJECT);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.pendingReviewCount()).isEqualTo(2);
    }

    @Test
    void pendingScoreBlocksPublish() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 1, 1, null));

        assertConflict(() -> service.publish(
                ACTOR, ACTIVITY_PROJECT, "0".repeat(64)), "PENDING_REVIEW_SCORES");
    }

    @Test
    void endedProjectCanPublish() {
        arrangePublish(null);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();

        var result = service.publish(ACTOR, ACTIVITY_PROJECT, fingerprint);

        assertThat(result.versionId()).isEqualTo(VERSION);
    }

    @Test
    void inProgressProjectCannotPublish() {
        arrangeProject(project("IN_PROGRESS", "HIGHER_BETTER", 0, 1, null));

        assertConflict(() -> service.publish(
                ACTOR, ACTIVITY_PROJECT, "0".repeat(64)), "RANKING_PUBLISH_NOT_ALLOWED");
    }

    @Test
    void emptyRankingCannotPublish() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 0, 0, null));

        assertConflict(() -> service.publish(
                ACTOR, ACTIVITY_PROJECT, "0".repeat(64)), "NO_EFFECTIVE_SCORES");
    }

    @Test
    void staleFingerprintCannotPublish() {
        arrangeProject(project("ENDED", "HIGHER_BETTER", 0, 1, null));

        assertThatThrownBy(() -> service.publish(
                ACTOR, ACTIVITY_PROJECT, "0".repeat(64)))
                .isInstanceOf(RankingSourceChangedException.class);
    }

    @Test
    void publicationWritesDefinitionVersionEntriesAndSources() {
        arrangePublish(null);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();

        service.publish(ACTOR, ACTIVITY_PROJECT, fingerprint);

        verify(publicationPort).createPublishedVersion(
                eq(DEFINITION), eq(1), eq(null), eq(ACTOR), anyMap(), anyMap());
        verify(publicationPort).saveEntries(eq(VERSION), any());
    }

    @Test
    void previousCurrentVersionBecomesReplaced() {
        UUID previous = UUID.randomUUID();
        arrangePublish(previous);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();

        service.publish(ACTOR, ACTIVITY_PROJECT, fingerprint);

        verify(publicationPort).markReplaced(previous);
    }

    @Test
    void currentDefinitionPointsToNewVersion() {
        arrangePublish(null);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();

        service.publish(ACTOR, ACTIVITY_PROJECT, fingerprint);

        verify(definitionPort).pointToCurrentVersion(DEFINITION, VERSION);
    }

    @Test
    void historyIsNotOverwritten() {
        UUID previous = UUID.randomUUID();
        arrangePublish(previous);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();

        service.publish(ACTOR, ACTIVITY_PROJECT, fingerprint);

        verify(publicationPort).markReplaced(previous);
        verify(publicationPort).createPublishedVersion(
                eq(DEFINITION), eq(1), eq(previous), eq(ACTOR), anyMap(), anyMap());
    }

    @Test
    void currentRankingCanBeWithdrawn() {
        arrangeWithdrawal(RankingVersionStatus.PUBLISHED, null);

        service.withdraw(ACTOR, ACTIVITY_PROJECT, "  incorrect score  ");

        verify(publicationPort).withdrawVersion(VERSION, ACTOR, "incorrect score");
    }

    @Test
    void withdrawalClearsCurrentVersion() {
        arrangeWithdrawal(RankingVersionStatus.PUBLISHED, null);

        service.withdraw(ACTOR, ACTIVITY_PROJECT, "reason");

        verify(definitionPort).clearCurrentVersion(DEFINITION, VERSION);
    }

    @Test
    void withdrawalRevokesAchievements() {
        arrangeWithdrawal(RankingVersionStatus.PUBLISHED, null);

        service.withdraw(ACTOR, ACTIVITY_PROJECT, "reason");

        verify(achievementService).revokeByRankingVersion(
                VERSION, ACTOR, "reason");
    }

    @Test
    void withdrawalDoesNotDeleteHistory() {
        arrangeWithdrawal(RankingVersionStatus.PUBLISHED, null);

        service.withdraw(ACTOR, ACTIVITY_PROJECT, "reason");

        verify(publicationPort).withdrawVersion(VERSION, ACTOR, "reason");
        verify(publicationPort, never()).markReplaced(VERSION);
    }

    @Test
    void oldHistoricalVersionCannotBeWithdrawn() {
        arrangeWithdrawal(RankingVersionStatus.REPLACED, null);

        assertConflict(() -> service.withdraw(
                ACTOR, ACTIVITY_PROJECT, "reason"), "RANKING_VERSION_CONFLICT");
    }

    @Test
    void failedPublicationRollsBackEverything() {
        arrangePublish(null);
        String fingerprint = service.preview(ACTOR, ACTIVITY_PROJECT).sourceFingerprint();
        doThrow(new IllegalStateException("write failed"))
                .when(publicationPort).saveEntries(eq(VERSION), any());

        assertThatThrownBy(() -> service.publish(
                ACTOR, ACTIVITY_PROJECT, fingerprint))
                .isInstanceOf(IllegalStateException.class);
        verify(definitionPort, never()).pointToCurrentVersion(any(), any());
    }

    private void arrangeProject(RankingProjectDetail project) {
        when(rankingQuery.findProject(SCHOOL, ACTIVITY_PROJECT))
                .thenReturn(Optional.of(project));
        lenient().when(sourceQuery.findCurrentEffectiveApprovedSources(
                SCHOOL, ACTIVITY_PROJECT)).thenReturn(List.of(source(100)));
    }

    private void arrangePublish(UUID currentVersion) {
        RankingProjectDetail project =
                project("ENDED", "HIGHER_BETTER", 0, 1, currentVersion);
        arrangeProject(project);
        List<RankingScoreSource> sources = List.of(source(100));
        when(sourceQuery.findCurrentEffectiveApprovedSources(
                SCHOOL, ACTIVITY_PROJECT)).thenReturn(sources);
        when(sourceQuery.lockCurrentEffectiveApprovedSources(
                SCHOOL, ACTIVITY_PROJECT)).thenReturn(sources);
        when(definitionPort.getOrCreateAndLock(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new RankingDefinitionPort.LockedDefinition(
                        DEFINITION, currentVersion));
        when(publicationPort.nextVersionNumber(DEFINITION)).thenReturn(1);
        when(publicationPort.createPublishedVersion(
                eq(DEFINITION), anyInt(), eq(currentVersion), eq(ACTOR),
                anyMap(), anyMap())).thenReturn(VERSION);
        lenient().when(rankingQuery.findVersion(SCHOOL, VERSION))
                .thenReturn(Optional.of(version()));
    }

    private void arrangeWithdrawal(
            RankingVersionStatus status, Instant withdrawnAt) {
        arrangeProject(project(
                "ENDED", "HIGHER_BETTER", 0, 1, VERSION));
        when(definitionPort.lockExisting(SCHOOL, ACTIVITY_PROJECT))
                .thenReturn(Optional.of(
                        new RankingDefinitionPort.LockedDefinition(
                                DEFINITION, VERSION)));
        when(publicationPort.lockVersion(VERSION))
                .thenReturn(Optional.of(
                        new RankingPublicationPort.LockedVersion(
                                VERSION, status, withdrawnAt)));
    }

    private static RankingProjectDetail project(
            String status,
            String direction,
            long pending,
            long approved,
            UUID currentVersion) {
        boolean enabled = !"NO_RANKING".equals(direction);
        return new RankingProjectDetail(
                ACTIVITY_PROJECT,
                ACTIVITY,
                "Activity",
                status,
                PROJECT,
                "Project",
                "INTEGER",
                "points",
                direction,
                "BEST",
                true,
                approved,
                pending,
                enabled ? (currentVersion == null
                        ? RankingStatus.NOT_PUBLISHED : RankingStatus.CURRENT)
                        : RankingStatus.DISABLED,
                currentVersion,
                currentVersion == null ? null : 1,
                currentVersion == null ? null : approved,
                currentVersion == null ? null : Instant.now(),
                currentVersion == null ? null : RankingVersionStatus.PUBLISHED,
                enabled && ("IN_PROGRESS".equals(status) || "ENDED".equals(status)),
                enabled && "ENDED".equals(status) && pending == 0 && approved > 0,
                Instant.now().minusSeconds(3600),
                Instant.now(),
                "Gym",
                "Description",
                "Rules",
                null,
                0,
                RULE,
                null,
                null,
                null);
    }

    private static RankingScoreSource source(int score) {
        return new RankingScoreSource(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Student",
                "School",
                "INTEGER",
                BigDecimal.valueOf(score),
                null,
                null,
                Instant.parse("2026-07-30T08:00:00Z"),
                RULE,
                0);
    }

    private static RankingVersionDetail version() {
        return new RankingVersionDetail(
                VERSION,
                1,
                RankingVersionStatus.PUBLISHED,
                1,
                ACTOR,
                "Admin",
                Instant.now(),
                null,
                null,
                null,
                null,
                "MANUAL",
                ACTIVITY_PROJECT,
                "Activity",
                "Project",
                "INTEGER",
                "points",
                "HIGHER_BETTER",
                "BEST",
                TiePolicy.COMPETITION,
                null,
                true,
                0,
                RULE,
                "a".repeat(64),
                List.of());
    }

    private static void assertConflict(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RankingConflictException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
