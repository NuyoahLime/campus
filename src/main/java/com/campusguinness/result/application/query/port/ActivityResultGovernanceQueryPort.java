package com.campusguinness.result.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.GovernanceActivityResultDetail;
import com.campusguinness.result.application.query.model.GovernanceActivityResultSummary;

import java.util.Optional;
import java.util.UUID;

public interface ActivityResultGovernanceQueryPort {
    QueryPage<GovernanceActivityResultSummary> findGovernance(
            int page, int size, String publicStatus, Boolean blocked, String query);

    Optional<GovernanceActivityResultDetail> findGovernanceById(UUID resultId);
}
