package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementWithdrawalRegressionIT
        extends AchievementIntegrationTestSupport {

    @Autowired AchievementRecordService compatibilityFacade;
    @Autowired StudentAchievementApplicationService studentService;
    @Autowired PublicAchievementVerificationService publicService;

    @Test
    void rankingWithdrawalRevokesActiveRecordsWithoutDeletingThem() {
        var version = publishRanking();
        var record = issueRecord(version);

        rankingService.withdraw(adminId, activityProjectId, "ranking error");

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM achievement_records WHERE id=?",
                        String.class,
                        record.recordId()))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM achievement_records WHERE id=?",
                        Long.class,
                        record.recordId()))
                .isEqualTo(1);
    }

    @Test
    void rankingWithdrawalKeepsVerificationCode() {
        var version = publishRanking();
        var record = issueRecord(version);

        rankingService.withdraw(adminId, activityProjectId, "ranking error");

        assertThat(jdbc.queryForObject(
                        "SELECT verification_code FROM achievement_records WHERE id=?",
                        String.class,
                        record.recordId()))
                .isEqualTo(record.verificationCode());
    }

    @Test
    void publicVerificationAndStudentDetailShowRevoked() {
        var version = publishRanking();
        var record = issueRecord(version);

        rankingService.withdraw(adminId, activityProjectId, "ranking error");

        assertThat(publicService.verify(record.verificationCode()).valid())
                .isFalse();
        assertThat(publicService.verify(record.verificationCode()).status())
                .isEqualTo(AchievementStatus.REVOKED);
        assertThat(studentService.get(studentId, record.recordId()).status())
                .isEqualTo(AchievementStatus.REVOKED);
        assertThat(studentService.get(
                        studentId, record.recordId()).revocationReason())
                .isEqualTo("ranking error");
    }

    @Test
    void repeatedRevocationDoesNotOverwriteFirstAudit() {
        var version = publishRanking();
        var record = issueRecord(version);
        rankingService.withdraw(adminId, activityProjectId, "first reason");
        Instant firstRevokedAt = jdbc.queryForObject(
                        "SELECT revoked_at FROM achievement_records WHERE id=?",
                        java.sql.Timestamp.class,
                        record.recordId())
                .toInstant();

        compatibilityFacade.revokeByRankingVersion(
                version.versionId(), otherAdminId, "second reason");

        var audit = jdbc.queryForMap("""
                SELECT revoked_at, revoked_by, revocation_reason
                FROM achievement_records
                WHERE id=?
                """, record.recordId());
        assertThat(((java.sql.Timestamp) audit.get("revoked_at")).toInstant())
                .isEqualTo(firstRevokedAt);
        assertThat(audit.get("revoked_by")).isEqualTo(adminId);
        assertThat(audit.get("revocation_reason")).isEqualTo("first reason");
    }

    @Test
    void normalRankingReplacementDoesNotRevokeRecords() {
        var firstVersion = publishRanking();
        var record = issueRecord(firstVersion);

        var secondVersion = publishRanking();

        assertThat(secondVersion.versionNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM achievement_records WHERE id=?",
                        String.class,
                        record.recordId()))
                .isEqualTo("ACTIVE");
        assertThat(publicService.verify(record.verificationCode()).valid())
                .isTrue();
    }
}
