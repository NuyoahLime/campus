package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.port.AchievementIssuancePort;
import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.query.model.PublicAchievementVerification;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility facade for the original achievement-record endpoints and the
 * ranking-withdrawal integration.
 */
@Service
@Transactional
public class AchievementRecordService {

    private final SchoolAdminAchievementApplicationService schoolAdminService;
    private final StudentAchievementApplicationService studentService;
    private final PublicAchievementVerificationService publicService;
    private final AchievementRecordQueryPort records;
    private final AchievementIssuancePort issuance;

    public AchievementRecordService(
            SchoolAdminAchievementApplicationService schoolAdminService,
            StudentAchievementApplicationService studentService,
            PublicAchievementVerificationService publicService,
            AchievementRecordQueryPort records,
            AchievementIssuancePort issuance) {
        this.schoolAdminService = schoolAdminService;
        this.studentService = studentService;
        this.publicService = publicService;
        this.records = records;
        this.issuance = issuance;
    }

    public record Record(
            UUID id,
            UUID activityProjectId,
            UUID studentId,
            int rank,
            String scoreValue,
            String storageType,
            String title,
            String verificationCode,
            String status,
            Instant issuedAt,
            UUID issuedBy,
            Instant revokedAt,
            String revocationReason) {
    }

    public Record issue(
            UUID activityProjectId, UUID rankingEntryId, UUID issuedBy) {
        return fromAdmin(schoolAdminService.issueAsSuperAdmin(
                issuedBy, activityProjectId, rankingEntryId).record());
    }

    @Transactional(readOnly = true)
    public List<Record> listMine(UUID studentId) {
        return studentService.list(studentId, null, null, 0, 100)
                .items().stream().map(AchievementRecordService::fromStudentItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Record> getMine(UUID id, UUID studentId) {
        return records.findStudentRecord(studentId, id)
                .map(AchievementRecordService::fromStudentDetail);
    }

    @Transactional(readOnly = true)
    public List<Record> listByProject(UUID activityProjectId) {
        return schoolAdminService.listForSuperAdmin(activityProjectId)
                .stream().map(AchievementRecordService::fromAdmin).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Record> verify(String verificationCode) {
        try {
            return Optional.of(fromPublic(publicService.verify(
                    verificationCode)));
        } catch (com.campusguinness.achievement.application.exception
                .AchievementNotFoundException ignored) {
            return Optional.empty();
        }
    }

    public void revokeByRankingVersion(
            UUID versionId, UUID revokedBy, String reason) {
        issuance.revokeByRankingVersion(versionId, revokedBy, reason);
    }

    private static Record fromAdmin(SchoolAdminAchievementDetail value) {
        return new Record(
                value.recordId(),
                value.activityProjectId(),
                value.studentId(),
                value.rankPosition(),
                value.scoreDisplayValue(),
                value.scoreStorageType(),
                value.recordTitle(),
                value.verificationCode(),
                value.status().name(),
                value.issuedAt(),
                value.issuedBy(),
                value.revokedAt(),
                value.revocationReason());
    }

    private static Record fromStudentItem(AchievementRecordItem value) {
        return new Record(
                value.recordId(),
                null,
                null,
                value.rankPosition(),
                value.scoreDisplayValue(),
                value.scoreStorageType(),
                value.recordTitle(),
                value.verificationCode(),
                value.status().name(),
                value.issuedAt(),
                null,
                value.revokedAt(),
                null);
    }

    private static Record fromStudentDetail(AchievementRecordDetail value) {
        return new Record(
                value.recordId(),
                value.activityProjectId(),
                null,
                value.rankPosition(),
                value.scoreDisplayValue(),
                value.scoreStorageType(),
                value.recordTitle(),
                value.verificationCode(),
                value.status().name(),
                value.issuedAt(),
                null,
                value.revokedAt(),
                value.revocationReason());
    }

    private static Record fromPublic(PublicAchievementVerification value) {
        return new Record(
                null,
                null,
                null,
                value.rankPosition(),
                value.scoreDisplayValue(),
                value.scoreStorageType(),
                value.recordTitle(),
                null,
                value.status().name(),
                value.issuedAt(),
                null,
                value.revokedAt(),
                null);
    }
}
