package com.campusguinness.result.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.ActivityResultHistoryEntry;
import com.campusguinness.result.application.query.model.ActivityResultStudentReadState;
import com.campusguinness.result.application.query.model.ManagementActivityResultDetail;
import com.campusguinness.result.application.query.model.ManagementActivityResultSummary;
import com.campusguinness.result.application.query.model.PublicActivityResultView;
import com.campusguinness.result.application.query.model.ResultFormatOverlayProjection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityResultReadQueryPort {
    Optional<PublicActivityResultView> findPublicByActivityId(UUID activityId);

    Optional<ActivityResultStudentReadState> findStudentState(UUID activityId, UUID schoolId);

    Optional<ResultFormatOverlayProjection> findFormatOverlay(UUID resultId, UUID resultVersionId);

    Optional<ManagementActivityResultDetail> findManagementDetail(UUID activityId, UUID schoolId);

    QueryPage<ManagementActivityResultSummary> findManagementList(
            UUID schoolId, int page, int size, String internalStatus, String publicStatus, String query);

    boolean existsActivity(UUID activityId, UUID schoolId);

    List<ActivityResultHistoryEntry> findHistory(UUID activityId, UUID schoolId);
}
