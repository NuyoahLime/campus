package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.exception.AchievementVerificationCodeCollisionException;
import com.campusguinness.achievement.application.exception.AchievementVerificationCodeGenerationException;
import com.campusguinness.achievement.application.port.AchievementIssuancePort;
import com.campusguinness.achievement.application.query.model.AchievementIssueResult;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementItem;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementStatus;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SchoolAdminAchievementApplicationService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "REVOKED");
    private static final int MAX_CODE_ATTEMPTS = 3;

    private final SchoolMembershipQueryPort memberships;
    private final AchievementIssuancePort issuance;
    private final AchievementRecordQueryPort records;
    private final Supplier<String> codeGenerator;

    @Autowired
    public SchoolAdminAchievementApplicationService(
            SchoolMembershipQueryPort memberships,
            AchievementIssuancePort issuance,
            AchievementRecordQueryPort records) {
        this(
                memberships,
                issuance,
                records,
                SchoolAdminAchievementApplicationService::newCode);
    }

    SchoolAdminAchievementApplicationService(
            SchoolMembershipQueryPort memberships,
            AchievementIssuancePort issuance,
            AchievementRecordQueryPort records,
            Supplier<String> codeGenerator) {
        this.memberships = memberships;
        this.issuance = issuance;
        this.records = records;
        this.codeGenerator = codeGenerator;
    }

    @Transactional
    public AchievementIssueResult issue(
            UUID actorId, UUID rankingEntryId) {
        return issueForSchool(requireSchoolId(actorId), rankingEntryId, actorId);
    }

    @Transactional
    public AchievementIssueResult issueAsSuperAdmin(
            UUID actorId,
            UUID activityProjectId,
            UUID rankingEntryId) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try {
                return issuance.issueForActivityProject(
                                activityProjectId,
                                rankingEntryId,
                                actorId,
                                codeGenerator.get())
                        .orElseThrow(AchievementNotFoundException::new);
            } catch (AchievementVerificationCodeCollisionException collision) {
                if (attempt == MAX_CODE_ATTEMPTS - 1) {
                    throw new AchievementVerificationCodeGenerationException();
                }
            }
        }
        throw new AchievementVerificationCodeGenerationException();
    }

    @Transactional(readOnly = true)
    public QueryPage<SchoolAdminAchievementItem> listProjectRecords(
            UUID actorId,
            UUID activityProjectId,
            String status,
            String keyword,
            int page,
            int size) {
        UUID schoolId = requireSchoolId(actorId);
        validatePage(page, size);
        requireSchoolProject(schoolId, activityProjectId);
        return records.findSchoolProjectRecords(
                schoolId,
                activityProjectId,
                normalizeStatus(status),
                normalizeKeyword(keyword),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public SchoolAdminAchievementDetail get(
            UUID actorId, UUID recordId) {
        return records.findSchoolRecord(requireSchoolId(actorId), recordId)
                .orElseThrow(AchievementNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<SchoolAdminAchievementStatus> getVersionStatuses(
            UUID actorId, UUID rankingVersionId) {
        UUID schoolId = requireSchoolId(actorId);
        if (!records.existsSchoolL1Version(schoolId, rankingVersionId)) {
            throw new AchievementNotFoundException();
        }
        return records.findVersionStatuses(schoolId, rankingVersionId);
    }

    @Transactional(readOnly = true)
    public List<SchoolAdminAchievementDetail> listForSuperAdmin(
            UUID activityProjectId) {
        return records.findProjectRecords(activityProjectId);
    }

    private AchievementIssueResult issueForSchool(
            UUID schoolId, UUID rankingEntryId, UUID actorId) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try {
                return issuance.issueForSchool(
                                schoolId,
                                rankingEntryId,
                                actorId,
                                codeGenerator.get())
                        .orElseThrow(AchievementNotFoundException::new);
            } catch (AchievementVerificationCodeCollisionException collision) {
                if (attempt == MAX_CODE_ATTEMPTS - 1) {
                    throw new AchievementVerificationCodeGenerationException();
                }
            }
        }
        throw new AchievementVerificationCodeGenerationException();
    }

    private UUID requireSchoolId(UUID actorId) {
        return memberships.findActiveSchoolAdminSchoolId(actorId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Active school administrator membership required"));
    }

    private void requireSchoolProject(
            UUID schoolId, UUID activityProjectId) {
        if (!records.existsSchoolProject(schoolId, activityProjectId)) {
            throw new AchievementNotFoundException();
        }
    }

    static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100");
        }
    }

    static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        if (value.length() > 100) {
            throw new IllegalArgumentException(
                    "keyword must not exceed 100 characters");
        }
        return value.isEmpty() ? null : value;
    }

    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim();
        if (!STATUSES.contains(value)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return value;
    }

    private static String newCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }
}
