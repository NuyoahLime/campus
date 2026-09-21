package com.campusguinness.result.application.query;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.ActivityResultHistoryEntry;
import com.campusguinness.result.application.query.model.ActivityResultStudentReadState;
import com.campusguinness.result.application.query.model.ActivityResultVersionProjection;
import com.campusguinness.result.application.query.model.ManagementActivityResultDetail;
import com.campusguinness.result.application.query.model.ManagementActivityResultSummary;
import com.campusguinness.result.application.query.model.PublicActivityResultView;
import com.campusguinness.result.application.query.model.StudentActivityResultView;
import com.campusguinness.result.application.query.model.ResultFormatOverlayProjection;
import com.campusguinness.result.application.query.port.ActivityResultReadQueryPort;
import com.campusguinness.result.internal.domain.ResultInternalStatus;
import com.campusguinness.result.internal.domain.ResultPublicStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ActivityResultReadQueryService {
    private static final int QUERY_MAX = 200;

    private final ActivityResultReadQueryPort query;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final StudentSchoolScopeAuthorization studentAuthorization;

    public ActivityResultReadQueryService(
            ActivityResultReadQueryPort query,
            SchoolResourceAuthorization schoolAuthorization,
            StudentSchoolScopeAuthorization studentAuthorization) {
        this.query = query;
        this.schoolAuthorization = schoolAuthorization;
        this.studentAuthorization = studentAuthorization;
    }

    public PublicActivityResultView publicDetail(UUID activityId) {
        requireActivityId(activityId);
        try {
            return query.findPublicByActivityId(activityId)
                    .orElseThrow(() -> notFound(activityId));
        } catch (ActivityResultReadConsistencyException ex) {
            throw notFound(activityId);
        }
    }

    public StudentActivityResultView studentDetail(UUID activityId) {
        requireActivityId(activityId);
        UUID schoolId = studentAuthorization.requireUniqueActiveStudent().schoolId();
        ActivityResultStudentReadState state;
        try {
            state = query.findStudentState(activityId, schoolId)
                    .orElseThrow(() -> notFound(activityId));
        } catch (ActivityResultReadConsistencyException ex) {
            throw notFound(activityId);
        }

        if (internalAllowed(state)) {
            if (state.internalProjection() == null) {
                throw notFound(activityId);
            }
            return toStudentView(state, state.internalProjection(), "INTERNAL");
        }
        if (publicAllowed(state)) {
            if (state.publicProjection() == null) {
                throw notFound(activityId);
            }
            return toStudentView(state, state.publicProjection(), "PUBLIC");
        }
        throw notFound(activityId);
    }

    public ManagementActivityResultDetail managementDetail(UUID activityId) {
        requireActivityId(activityId);
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        ManagementActivityResultDetail detail = query.findManagementDetail(activityId, schoolId)
                .orElseThrow(() -> notFound(activityId));
        requireConsistentPointers(detail);
        return detail;
    }

    public QueryPage<ManagementActivityResultSummary> managementList(
            int page, int size, String internalStatus, String publicStatus, String textQuery) {
        validatePage(page, size);
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        return query.findManagementList(
                schoolId,
                page,
                size,
                normalizeStatus(internalStatus, true),
                normalizeStatus(publicStatus, false),
                normalizeQuery(textQuery));
    }

    public List<ActivityResultHistoryEntry> history(UUID activityId) {
        requireActivityId(activityId);
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        if (!query.existsActivity(activityId, schoolId)) {
            throw notFound(activityId);
        }
        return query.findHistory(activityId, schoolId);
    }

    private boolean internalAllowed(ActivityResultStudentReadState state) {
        if (!ResultInternalStatus.INTERNAL_PUBLISHED.name().equals(state.internalStatus())
                || state.currentInternalVersionId() == null
                || ResultPublicStatus.PLATFORM_TAKEDOWN.name().equals(state.publicStatus())
                || ResultPublicStatus.ANOMALY_PENDING.name().equals(state.publicStatus())) {
            return false;
        }
        return !state.publicVisibilityBlocked()
                || (state.currentCandidateVersionId() != null
                    && state.currentCandidateVersionId().equals(state.currentInternalVersionId()));
    }

    private boolean publicAllowed(ActivityResultStudentReadState state) {
        return state.currentPublicVersionId() != null
                && !state.publicVisibilityBlocked()
                && !ResultPublicStatus.ANOMALY_PENDING.name().equals(state.publicStatus())
                && !ResultPublicStatus.PLATFORM_TAKEDOWN.name().equals(state.publicStatus());
    }

    private StudentActivityResultView toStudentView(
            ActivityResultStudentReadState state,
            ActivityResultVersionProjection version,
            String source) {
        ResultFormatOverlayProjection overlay;
        try {
            overlay = query.findFormatOverlay(state.resultId(), version.versionId()).orElse(null);
        } catch (ActivityResultReadConsistencyException ex) {
            throw notFound(state.activityId());
        }
        return new StudentActivityResultView(
                state.activityId(), state.resultId(), version.versionId(), version.versionNumber(),
                version.title(), version.summaryText(), version.scoreHighlights(), source,
                version.publishedInternallyAt(), version.publishedPubliclyAt(),
                overlay == null ? null : overlay.presentation());
    }

    private void requireConsistentPointers(ManagementActivityResultDetail detail) {
        requireProjection(detail.currentCandidateVersionId(), detail.candidateProjection(), "candidate");
        requireProjection(detail.currentInternalVersionId(), detail.internalProjection(), "internal");
        requireProjection(detail.currentPublicVersionId(), detail.publicProjection(), "public");
    }

    private void requireProjection(UUID pointer, ActivityResultVersionProjection projection, String name) {
        if (pointer != null && projection == null) {
            throw new ActivityResultReadConsistencyException(
                    "ActivityResult " + name + " pointer does not reference its exact version");
        }
    }

    private String normalizeStatus(String value, boolean internal) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            if (internal) ResultInternalStatus.valueOf(normalized);
            else ResultPublicStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + (internal ? "internalStatus" : "publicStatus"));
        }
        return normalized;
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > QUERY_MAX) throw new IllegalArgumentException("q max 200 chars");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }

    private void requireActivityId(UUID activityId) {
        if (activityId == null) throw new IllegalArgumentException("activityId required");
    }

    private IllegalArgumentException notFound(UUID activityId) {
        return new IllegalArgumentException("ActivityResult not found for activity: " + activityId);
    }
}
