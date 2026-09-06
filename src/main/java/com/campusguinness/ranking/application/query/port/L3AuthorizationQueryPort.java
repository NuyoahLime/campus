package com.campusguinness.ranking.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.L3AuthorizationDetailResult;
import com.campusguinness.ranking.application.query.model.L3AuthorizationSummaryResult;

import java.util.Optional;
import java.util.UUID;

public interface L3AuthorizationQueryPort {
    QueryPage<L3AuthorizationSummaryResult> listForSchool(UUID schoolId, String status, UUID projectId, int page, int size);

    Optional<L3AuthorizationDetailResult> findForSchool(UUID id, UUID schoolId);

    QueryPage<L3AuthorizationSummaryResult> listForReview(String status, UUID schoolId, UUID projectId, int page, int size);

    Optional<L3AuthorizationDetailResult> findForReview(UUID id);
}
