package com.campusguinness.ranking.application.service;

import com.campusguinness.achievement.application.service.AchievementRecordService;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.exception.RankingConflictException;
import com.campusguinness.ranking.application.exception.RankingConfigurationException;
import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.exception.RankingSourceChangedException;
import com.campusguinness.ranking.application.port.RankingDefinitionPort;
import com.campusguinness.ranking.application.port.RankingPublicationPort;
import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingProjectItem;
import com.campusguinness.ranking.application.query.model.RankingPreviewResult;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;
import com.campusguinness.ranking.application.query.model.RankingStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.query.model.RankingVersionStatus;
import com.campusguinness.ranking.application.query.model.RankingVersionSummary;
import com.campusguinness.ranking.application.query.port.RankingScoreSourceQueryPort;
import com.campusguinness.ranking.application.query.port.SchoolAdminRankingQueryPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SchoolAdminRankingApplicationService {

    private static final Set<String> EXECUTION_STATUSES = Set.of(
            "DRAFT", "PUBLISHED", "IN_PROGRESS", "ENDED", "CANCELLED");
    private static final Set<String> RANKING_STATUSES = Set.of(
            "NOT_PUBLISHED", "CURRENT", "WITHDRAWN", "DISABLED");
    private static final Pattern FINGERPRINT =
            Pattern.compile("^[0-9a-f]{64}$");

    private final SchoolMembershipQueryPort membershipQuery;
    private final SchoolAdminRankingQueryPort rankingQuery;
    private final RankingScoreSourceQueryPort sourceQuery;
    private final RankingDefinitionPort definitionPort;
    private final RankingPublicationPort publicationPort;
    private final AchievementRecordService achievementService;

    public SchoolAdminRankingApplicationService(
            SchoolMembershipQueryPort membershipQuery,
            SchoolAdminRankingQueryPort rankingQuery,
            RankingScoreSourceQueryPort sourceQuery,
            RankingDefinitionPort definitionPort,
            RankingPublicationPort publicationPort,
            AchievementRecordService achievementService) {
        this.membershipQuery = membershipQuery;
        this.rankingQuery = rankingQuery;
        this.sourceQuery = sourceQuery;
        this.definitionPort = definitionPort;
        this.publicationPort = publicationPort;
        this.achievementService = achievementService;
    }

    @Transactional(readOnly = true)
    public QueryPage<RankingProjectItem> listProjects(
            UUID actorId,
            String executionStatus,
            String rankingStatus,
            String keyword,
            int page,
            int size) {
        UUID schoolId = requireSchoolId(actorId);
        validatePage(page, size);
        String normalizedExecution = normalizeEnum(
                executionStatus, EXECUTION_STATUSES, "executionStatus");
        String normalizedRanking = normalizeEnum(
                rankingStatus, RANKING_STATUSES, "rankingStatus");
        String normalizedKeyword = normalizeKeyword(keyword);
        return rankingQuery.findProjects(
                schoolId,
                normalizedExecution,
                normalizedRanking,
                normalizedKeyword,
                page,
                size);
    }

    @Transactional(readOnly = true)
    public RankingProjectDetail getProject(UUID actorId, UUID activityProjectId) {
        return requireProject(requireSchoolId(actorId), activityProjectId);
    }

    @Transactional(readOnly = true)
    public RankingPreviewResult preview(UUID actorId, UUID activityProjectId) {
        return previewForSchool(requireSchoolId(actorId), activityProjectId);
    }

    @Transactional
    public RankingVersionDetail publish(
            UUID actorId,
            UUID activityProjectId,
            String expectedSourceFingerprint) {
        UUID schoolId = requireSchoolId(actorId);
        return publishForSchool(
                schoolId,
                actorId,
                activityProjectId,
                normalizeFingerprint(expectedSourceFingerprint));
    }

    @Transactional(readOnly = true)
    public RankingVersionDetail getCurrent(
            UUID actorId, UUID activityProjectId) {
        UUID schoolId = requireSchoolId(actorId);
        requireProject(schoolId, activityProjectId);
        return rankingQuery.findCurrentVersion(schoolId, activityProjectId)
                .orElseThrow(() -> new RankingNotFoundException(
                        "Current ranking does not exist"));
    }

    @Transactional(readOnly = true)
    public QueryPage<RankingVersionSummary> getVersions(
            UUID actorId, UUID activityProjectId, int page, int size) {
        UUID schoolId = requireSchoolId(actorId);
        validatePage(page, size);
        requireProject(schoolId, activityProjectId);
        return rankingQuery.findVersions(
                schoolId, activityProjectId, page, size);
    }

    @Transactional(readOnly = true)
    public RankingVersionDetail getVersion(UUID actorId, UUID versionId) {
        UUID schoolId = requireSchoolId(actorId);
        return rankingQuery.findVersion(schoolId, versionId)
                .orElseThrow(() -> new RankingNotFoundException(
                        "Ranking version does not exist"));
    }

    @Transactional
    public void withdraw(
            UUID actorId, UUID activityProjectId, String reason) {
        withdrawForSchool(
                requireSchoolId(actorId),
                actorId,
                activityProjectId,
                normalizeReason(reason));
    }

    @Transactional(readOnly = true)
    public RankingPreviewResult previewAsSuperAdmin(UUID activityProjectId) {
        return previewForSchool(requireProjectSchool(activityProjectId), activityProjectId);
    }

    @Transactional
    public RankingVersionDetail publishAsSuperAdmin(
            UUID actorId, UUID activityProjectId) {
        UUID schoolId = requireProjectSchool(activityProjectId);
        RankingPreviewResult preview = previewForSchool(schoolId, activityProjectId);
        return publishForSchool(
                schoolId, actorId, activityProjectId, preview.sourceFingerprint());
    }

    @Transactional(readOnly = true)
    public RankingVersionDetail getCurrentAsSuperAdmin(UUID activityProjectId) {
        UUID schoolId = requireProjectSchool(activityProjectId);
        return rankingQuery.findCurrentVersion(schoolId, activityProjectId)
                .orElseThrow(() -> new RankingNotFoundException(
                        "Current ranking does not exist"));
    }

    @Transactional(readOnly = true)
    public QueryPage<RankingVersionSummary> getVersionsAsSuperAdmin(
            UUID activityProjectId, int page, int size) {
        validatePage(page, size);
        return rankingQuery.findVersions(
                requireProjectSchool(activityProjectId),
                activityProjectId,
                page,
                size);
    }

    @Transactional
    public void withdrawAsSuperAdmin(
            UUID actorId, UUID activityProjectId, String reason) {
        withdrawForSchool(
                requireProjectSchool(activityProjectId),
                actorId,
                activityProjectId,
                normalizeReason(reason));
    }

    private RankingPreviewResult previewForSchool(
            UUID schoolId, UUID activityProjectId) {
        RankingProjectDetail project = requireProject(schoolId, activityProjectId);
        requirePreviewAllowed(project);
        List<RankingScoreSource> sources =
                sourceQuery.findCurrentEffectiveApprovedSources(
                        schoolId, activityProjectId);
        return calculatePreview(project, sources);
    }

    private RankingVersionDetail publishForSchool(
            UUID schoolId,
            UUID actorId,
            UUID activityProjectId,
            String expectedFingerprint) {
        RankingProjectDetail initialProject =
                requireProject(schoolId, activityProjectId);
        requirePublishAllowed(initialProject);
        List<RankingScoreSource> initialSources =
                sourceQuery.findCurrentEffectiveApprovedSources(
                        schoolId, activityProjectId);
        RankingPreviewResult initialPreview =
                calculatePreview(initialProject, initialSources);
        requireFingerprint(expectedFingerprint, initialPreview.sourceFingerprint());

        RankingDefinitionPort.LockedDefinition definition =
                definitionPort.getOrCreateAndLock(
                        activityProjectId,
                        schoolId,
                        initialProject.projectId(),
                        initialProject.activityTitle() + " - "
                                + initialProject.projectName(),
                        initialProject.tiePolicy().name(),
                        actorId);

        List<RankingScoreSource> sources =
                sourceQuery.lockCurrentEffectiveApprovedSources(
                        schoolId, activityProjectId);
        RankingProjectDetail project =
                requireProject(schoolId, activityProjectId);
        requirePublishAllowed(project);
        RankingPreviewResult preview = calculatePreview(project, sources);
        requireFingerprint(expectedFingerprint, preview.sourceFingerprint());

        int versionNumber =
                publicationPort.nextVersionNumber(definition.definitionId());
        UUID versionId = publicationPort.createPublishedVersion(
                definition.definitionId(),
                versionNumber,
                definition.currentVersionId(),
                actorId,
                calculationSnapshot(project, preview),
                dataScopeSnapshot(schoolId, project, preview));
        publicationPort.saveEntries(versionId, preview.entries());
        if (definition.currentVersionId() != null) {
            publicationPort.markReplaced(definition.currentVersionId());
        }
        definitionPort.pointToCurrentVersion(
                definition.definitionId(), versionId);
        return rankingQuery.findVersion(schoolId, versionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Published ranking version cannot be read"));
    }

    private void withdrawForSchool(
            UUID schoolId,
            UUID actorId,
            UUID activityProjectId,
            String reason) {
        requireProject(schoolId, activityProjectId);
        RankingDefinitionPort.LockedDefinition definition =
                definitionPort.lockExisting(schoolId, activityProjectId)
                        .orElseThrow(() -> new RankingConflictException(
                                "NO_CURRENT_RANKING",
                                "No current ranking can be withdrawn"));
        if (definition.currentVersionId() == null) {
            throw new RankingConflictException(
                    "NO_CURRENT_RANKING",
                    "No current ranking can be withdrawn");
        }
        RankingPublicationPort.LockedVersion version =
                publicationPort.lockVersion(definition.currentVersionId())
                        .orElseThrow(() -> new RankingConflictException(
                                "RANKING_VERSION_CONFLICT",
                                "Current ranking version does not exist"));
        if (version.status() != RankingVersionStatus.PUBLISHED
                || version.withdrawnAt() != null) {
            throw new RankingConflictException(
                    "RANKING_VERSION_CONFLICT",
                    "Only the current published ranking can be withdrawn");
        }
        publicationPort.withdrawVersion(version.versionId(), actorId, reason);
        definitionPort.clearCurrentVersion(
                definition.definitionId(), version.versionId());
        achievementService.revokeByRankingVersion(
                version.versionId(), actorId, reason);
    }

    private RankingPreviewResult calculatePreview(
            RankingProjectDetail project, List<RankingScoreSource> sources) {
        if (project.currentRuleVersionId() == null) {
            throw new RankingConfigurationException(
                    "Project does not have a current rule version");
        }
        List<CalculatedRankingEntry> entries = RankingCalculator.rank(
                sources,
                project.scoreStorageType(),
                project.comparisonDirection(),
                project.gradeOrder(),
                project.allowTie(),
                project.decimalPlaces());
        String fingerprint =
                RankingSourceFingerprint.calculate(project, sources);
        List<String> warnings = new ArrayList<>();
        if ("IN_PROGRESS".equals(project.executionStatus())) {
            warnings.add("活动尚未结束");
        }
        if (project.pendingReviewCount() > 0) {
            warnings.add("仍有待审核成绩");
        }
        if (entries.isEmpty()) {
            warnings.add("暂无当前有效成绩");
        }
        boolean publishable = "ENDED".equals(project.executionStatus())
                && project.pendingReviewCount() == 0
                && !entries.isEmpty();
        return new RankingPreviewResult(
                project.activityProjectId(),
                project.activityTitle(),
                project.projectName(),
                project.scoreStorageType(),
                project.scoreUnit(),
                project.comparisonDirection(),
                project.effectiveScoreRule(),
                project.tiePolicy(),
                fingerprint,
                entries.size(),
                project.pendingReviewCount(),
                publishable,
                List.copyOf(warnings),
                entries);
    }

    private RankingProjectDetail requireProject(
            UUID schoolId, UUID activityProjectId) {
        return rankingQuery.findProject(schoolId, activityProjectId)
                .orElseThrow(() -> new RankingNotFoundException(
                        "Activity project does not exist"));
    }

    private UUID requireSchoolId(UUID actorId) {
        return membershipQuery.findActiveSchoolAdminSchoolId(actorId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }

    private UUID requireProjectSchool(UUID activityProjectId) {
        return rankingQuery.findSchoolId(activityProjectId)
                .orElseThrow(() -> new RankingNotFoundException(
                        "Activity project does not exist"));
    }

    private static void requirePreviewAllowed(RankingProjectDetail project) {
        requireRankingEnabled(project);
        if (!"IN_PROGRESS".equals(project.executionStatus())
                && !"ENDED".equals(project.executionStatus())) {
            throw new RankingConflictException(
                    "RANKING_PREVIEW_NOT_ALLOWED",
                    "Activity status does not allow ranking preview");
        }
    }

    private static void requirePublishAllowed(RankingProjectDetail project) {
        requireRankingEnabled(project);
        if (!"ENDED".equals(project.executionStatus())) {
            throw new RankingConflictException(
                    "RANKING_PUBLISH_NOT_ALLOWED",
                    "Only ended activities can publish rankings");
        }
        if (project.pendingReviewCount() > 0) {
            throw new RankingConflictException(
                    "PENDING_REVIEW_SCORES",
                    "Pending review scores must be resolved before publication");
        }
        if (project.approvedEffectiveScoreCount() == 0) {
            throw new RankingConflictException(
                    "NO_EFFECTIVE_SCORES",
                    "At least one current effective approved score is required");
        }
    }

    private static void requireRankingEnabled(RankingProjectDetail project) {
        if ("NO_RANKING".equals(project.comparisonDirection())
                || project.rankingStatus() == RankingStatus.DISABLED) {
            throw new RankingConflictException(
                    "RANKING_DISABLED_FOR_PROJECT",
                    "Ranking is disabled for this project");
        }
    }

    private static void requireFingerprint(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RankingSourceChangedException();
        }
    }

    private static Map<String, Object> calculationSnapshot(
            RankingProjectDetail project, RankingPreviewResult preview) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scoreStorageType", project.scoreStorageType());
        snapshot.put("comparisonDirection", project.comparisonDirection());
        snapshot.put("effectiveScoreRule", project.effectiveScoreRule());
        snapshot.put("gradeOrder", project.gradeOrder());
        snapshot.put("allowTie", project.allowTie());
        snapshot.put("tiePolicy", project.tiePolicy().name());
        snapshot.put("scoreUnit", project.scoreUnit());
        snapshot.put("decimalPlaces", project.decimalPlaces());
        snapshot.put("sourceFingerprint", preview.sourceFingerprint());
        snapshot.put("currentRuleVersionId", project.currentRuleVersionId());
        return snapshot;
    }

    private static Map<String, Object> dataScopeSnapshot(
            UUID schoolId,
            RankingProjectDetail project,
            RankingPreviewResult preview) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schoolId", schoolId);
        snapshot.put("activityId", project.activityId());
        snapshot.put("activityProjectId", project.activityProjectId());
        snapshot.put("projectId", project.projectId());
        snapshot.put("includedStudentCount", preview.totalRanked());
        snapshot.put("includedScoreAttemptCount", preview.totalRanked());
        return snapshot;
    }

    private static String normalizeFingerprint(String fingerprint) {
        String normalized = fingerprint == null ? "" : fingerprint.trim();
        if (!FINGERPRINT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "expectedSourceFingerprint must be 64 lowercase hexadecimal characters");
        }
        return normalized;
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Withdrawal reason is required");
        }
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(
                    "Withdrawal reason must not exceed 1000 characters");
        }
        return normalized;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "keyword must not exceed 100 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeEnum(
            String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
