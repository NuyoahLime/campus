package com.campusguinness.result.application.query;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.GovernanceActivityResultDetail;
import com.campusguinness.result.application.query.model.GovernanceActivityResultSummary;
import com.campusguinness.result.application.query.port.ActivityResultGovernanceQueryPort;
import com.campusguinness.result.internal.domain.ResultPublicStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ActivityResultGovernanceQueryService {
    private final ActivityResultGovernanceQueryPort query;
    private final PlatformGovernanceAuthorization authorization;

    public ActivityResultGovernanceQueryService(
            ActivityResultGovernanceQueryPort query,
            PlatformGovernanceAuthorization authorization) {
        this.query = query;
        this.authorization = authorization;
    }

    public QueryPage<GovernanceActivityResultSummary> list(
            int page, int size, String publicStatus, Boolean blocked, String textQuery) {
        authorization.requireSuperAdmin();
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return query.findGovernance(page, size, normalizePublicStatus(publicStatus), blocked, normalizeQuery(textQuery));
    }

    public GovernanceActivityResultDetail detail(UUID resultId) {
        authorization.requireSuperAdmin();
        if (resultId == null) throw new IllegalArgumentException("ActivityResult id required");
        return query.findGovernanceById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult governance resource not found: " + resultId));
    }

    private String normalizePublicStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            ResultPublicStatus.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid publicStatus");
        }
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("q max 200 chars");
        return normalized.toLowerCase(Locale.ROOT);
    }
}
