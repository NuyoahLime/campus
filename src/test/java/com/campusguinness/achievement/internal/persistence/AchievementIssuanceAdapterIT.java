package com.campusguinness.achievement.internal.persistence;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.exception.AchievementVerificationCodeCollisionException;
import com.campusguinness.achievement.application.port.AchievementIssuancePort;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchievementIssuanceAdapterIT extends AchievementIntegrationTestSupport {

    @Autowired AchievementIssuancePort issuance;
    @Autowired TransactionTemplate transactions;

    @Test
    void snapshotsComeFromRankingVersion() {
        RankingVersionDetail version = publishRanking();

        var record = issueRecord(version);

        assertThat(record.rankingVersionId()).isEqualTo(version.versionId());
        assertThat(record.rankingVersionNumber()).isEqualTo(version.versionNumber());
        assertThat(record.rankingEntryId()).isEqualTo(firstEntryId(version));
        assertThat(record.rankPosition())
                .isEqualTo(version.entries().getFirst().rankPosition());
        assertThat(record.scoreDisplayValue())
                .isEqualTo(version.entries().getFirst().scoreDisplayValue());
        assertThat(record.schoolName()).isEqualTo("Ranking School " + fixtureSuffix);
        assertThat(record.activityTitle()).isEqualTo(version.activityTitle());
        assertThat(record.projectName()).isEqualTo(version.projectName());
    }

    @Test
    void recordTitleIsServerGenerated() {
        RankingVersionDetail version = publishRanking();

        var record = issueRecord(version);

        assertThat(record.recordTitle()).isEqualTo(
                version.activityTitle()
                        + " · "
                        + version.projectName()
                        + " · 第1名");
    }

    @Test
    void verificationCodeIsLowercaseHex32() {
        var record = issueRecord(publishRanking());

        assertThat(record.verificationCode()).matches("[0-9a-f]{32}");
        assertThat(jdbc.queryForObject(
                        "SELECT verification_code FROM achievement_records WHERE id=?",
                        String.class,
                        record.recordId()))
                .isEqualTo(record.verificationCode());
    }

    @Test
    void duplicateIssueKeepsOriginalSnapshotAndAuditFields() {
        RankingVersionDetail version = publishRanking();
        var first = achievementService.issue(
                adminId, firstEntryId(version));
        var duplicate = achievementService.issue(
                adminId, firstEntryId(version));

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.record().recordId())
                .isEqualTo(first.record().recordId());
        assertThat(duplicate.record().verificationCode())
                .isEqualTo(first.record().verificationCode());
        assertThat(duplicate.record().issuedAt())
                .isEqualTo(first.record().issuedAt());
        assertThat(duplicate.record().issuedBy())
                .isEqualTo(first.record().issuedBy());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM achievement_records WHERE ranking_entry_id=?",
                        Long.class,
                        firstEntryId(version)))
                .isEqualTo(1);
    }

    @Test
    void verificationCodeCollisionCanRetryInsideOuterTransaction() {
        UUID secondStudent = createUser(
                "ranking-student-two-" + fixtureSuffix);
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
        RankingVersionDetail version = publishRanking();
        var first = achievementService.issue(
                adminId, version.entries().get(0).rankingEntryId());
        UUID secondEntryId =
                version.entries().get(1).rankingEntryId();

        var retried = transactions.execute(ignored -> {
            assertThatThrownBy(() -> issuance.issueForSchool(
                            schoolId,
                            secondEntryId,
                            adminId,
                            first.record().verificationCode()))
                    .isInstanceOf(
                            AchievementVerificationCodeCollisionException.class);
            return issuance.issueForSchool(
                            schoolId,
                            secondEntryId,
                            adminId,
                            "b".repeat(32))
                    .orElseThrow();
        });

        assertThat(retried).isNotNull();
        assertThat(retried.created()).isTrue();
        assertThat(retried.record().verificationCode())
                .isEqualTo("b".repeat(32));
    }

    @Test
    void otherSchoolEntryLooksNotFound() {
        RankingVersionDetail version = publishRanking();

        assertThatThrownBy(() ->
                achievementService.issue(otherAdminId, firstEntryId(version)))
                .isInstanceOf(AchievementNotFoundException.class);
    }

    @Test
    void replacedVersionCannotBeIssued() {
        RankingVersionDetail historical = publishRanking();
        publishRanking();

        assertThatThrownBy(() ->
                achievementService.issue(adminId, firstEntryId(historical)))
                .isInstanceOf(AchievementNotFoundException.class);
    }

    @Test
    void withdrawnVersionCannotBeIssued() {
        RankingVersionDetail withdrawn = publishRanking();
        rankingService.withdraw(adminId, activityProjectId, "incorrect result");

        assertThatThrownBy(() ->
                achievementService.issue(adminId, firstEntryId(withdrawn)))
                .isInstanceOf(AchievementNotFoundException.class);
    }

    @Test
    void failedIssueDoesNotPersistRecord() {
        assertThatThrownBy(() ->
                achievementService.issue(adminId, UUID.randomUUID()))
                .isInstanceOf(AchievementNotFoundException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM achievement_records",
                Long.class)).isZero();
    }

    @Test
    void currentVersionStatusesReturnIssuedAndUnissuedRows() {
        RankingVersionDetail version = publishRanking();
        var issued = issueRecord(version);

        var statuses = achievementService.getVersionStatuses(
                adminId, version.versionId());

        assertThat(statuses).hasSize(1);
        assertThat(statuses.getFirst().rankingEntryId())
                .isEqualTo(firstEntryId(version));
        assertThat(statuses.getFirst().achievementRecordId())
                .isEqualTo(issued.recordId());
        assertThat(statuses.getFirst().achievementStatus())
                .isEqualTo(AchievementStatus.ACTIVE);
    }
}
