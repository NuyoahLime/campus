package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultFormatEditReadIntegrationIT extends ActivityResultReadTestSupport {
    private static final String PUBLIC = "/api/v1/public/activities/{activityId}/result";
    private static final String STUDENT = "/api/v1/student/activities/{activityId}/result";
    private static final String MANAGEMENT = "/api/v1/activities/{activityId}/result";
    private static final String HISTORY =
            "/api/v1/school-admin/activity-results/{resultId}/versions/{versionId}/format-history";

    @Test
    void missingHeadIsValidNoOverlayForEveryAudience() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "missing-head", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, 1, 1, 1);

        mvc.perform(get(PUBLIC, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryText").value("Summary V1 missing-head"))
                .andExpect(jsonPath("$.presentation").doesNotExist());
        mvc.perform(get(STUDENT, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryText").value("Summary V1 missing-head"))
                .andExpect(jsonPath("$.presentation").doesNotExist());
        mvc.perform(get(MANAGEMENT, fixture.activityId()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalProjection.summaryText").value("Summary V1 missing-head"))
                .andExpect(jsonPath("$.internalProjection.presentation").doesNotExist())
                .andExpect(jsonPath("$.internalProjection.currentFormatEditRecordId").doesNotExist())
                .andExpect(jsonPath("$.internalProjection.currentFormatRevision").doesNotExist());
    }

    @Test
    void malformedPublicOverlayFailsClosedAndNeverFallsBackToBase() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-public", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, 1, 1, 1);
        insertFormat(fixture.resultId(), fixture.v1(), 1, malformedPayload());

        mvc.perform(get(PUBLIC, fixture.activityId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedPreferredInternalOverlayFailsClosedWithoutPublicFallback() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-student", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, 2, 2, 1);
        insertFormat(fixture.resultId(), fixture.v1(), 1, payload(0, 4, "NORMAL"));
        insertFormat(fixture.resultId(), fixture.v2(), 1, malformedPayload());

        mvc.perform(get(STUDENT, fixture.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedManagementOverlayReturnsControlledConsistencyConflict() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-management-format", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 1, 1, null);
        insertFormat(fixture.resultId(), fixture.v1(), 1, malformedPayload());

        mvc.perform(get(MANAGEMENT, fixture.activityId()).with(adminA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_RESULT_DATA_CONSISTENCY"));
    }

    @Test
    void exactVersionOverlaysRemainIsolatedAndPublicStudentDtosHideAuditMetadata() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "overlay-isolation", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, 2, 2, 1);
        UUID v1Edit = insertFormat(fixture.resultId(), fixture.v1(), 1, payload(0, 4, "NORMAL"));
        UUID v2Edit = insertFormat(fixture.resultId(), fixture.v2(), 1, payload(0, 5, "HEADING"));

        mvc.perform(get(PUBLIC, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.presentation.paragraphs[0].end").value(4))
                .andExpect(jsonPath("$.formatEditId").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.editedBy").doesNotExist())
                .andExpect(jsonPath("$.formatHistory").doesNotExist());
        mvc.perform(get(STUDENT, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.presentation.paragraphs[0].end").value(5))
                .andExpect(jsonPath("$.formatEditId").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.editedBy").doesNotExist())
                .andExpect(jsonPath("$.formatHistory").doesNotExist());
        mvc.perform(get(MANAGEMENT, fixture.activityId()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicProjection.presentation.paragraphs[0].end").value(4))
                .andExpect(jsonPath("$.publicProjection.currentFormatEditRecordId").value(v1Edit.toString()))
                .andExpect(jsonPath("$.publicProjection.currentFormatRevision").value(1))
                .andExpect(jsonPath("$.internalProjection.presentation.paragraphs[0].end").value(5))
                .andExpect(jsonPath("$.internalProjection.currentFormatEditRecordId").value(v2Edit.toString()))
                .andExpect(jsonPath("$.internalProjection.currentFormatRevision").value(1));
    }

    @Test
    void makePublicStateRetainsOverlayBoundToExactVersion() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "made-public", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, null, 2, 2);
        insertFormat(fixture.resultId(), fixture.v2(), 1, payload(0, 5, "HEADING"));

        mvc.perform(get(PUBLIC, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.presentation.paragraphs[0].style").value("HEADING"));
    }

    @Test
    void takedownAndResetPreserveExactVersionHistoryWhilePublicReadStaysDenied() throws Exception {
        for (String status : new String[]{"PLATFORM_TAKEDOWN", "NOT_SUBMITTED"}) {
            Fixture fixture = insertResult(
                    schoolA, "blocked-" + status, "PUBLISHED", "INTERNAL_PUBLISHED", status, true,
                    null, 1, 1);
            insertFormat(fixture.resultId(), fixture.v1(), 1, payload(0, 4, "NORMAL"));

            mvc.perform(get(PUBLIC, fixture.activityId())).andExpect(status().isNotFound());
            mvc.perform(get(HISTORY, fixture.resultId(), fixture.v1()).with(adminA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].revision").value(1));
        }
    }

    @Test
    void historyIsVersionIsolatedAndOrderedByRevisionAscending() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "history-isolation", "PUBLISHED", "INTERNAL_PUBLISHED", "PUBLIC", false, 2, 2, 1);
        insertRecord(fixture.resultId(), fixture.v1(), 2, payload(0, 4, "HEADING"));
        insertRecord(fixture.resultId(), fixture.v1(), 1, payload(0, 4, "NORMAL"));
        insertRecord(fixture.resultId(), fixture.v2(), 1, payload(0, 5, "NORMAL"));

        mvc.perform(get(HISTORY, fixture.resultId(), fixture.v1()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revision").value(1))
                .andExpect(jsonPath("$[1].revision").value(2));
        mvc.perform(get(HISTORY, fixture.resultId(), fixture.v2()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].revision").value(1));
    }

    private UUID insertFormat(UUID resultId, UUID versionId, int revision, String payload) {
        UUID recordId = insertRecord(resultId, versionId, revision, payload);
        jdbc.update("""
                INSERT INTO result_format_heads(
                    result_version_id, result_id, current_format_edit_record_id, version)
                VALUES (?, ?, ?, ?)
                """, versionId, resultId, recordId, revision);
        return recordId;
    }

    private UUID insertRecord(UUID resultId, UUID versionId, int revision, String payload) {
        UUID recordId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result_format_edit_records(
                    id, result_id, result_version_id, revision, payload, reason, edited_by)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'format read test', ?)
                """, recordId, resultId, versionId, revision, payload, adminA);
        return recordId;
    }

    private String payload(int start, int end, String style) {
        return """
                {"paragraphs":[{"start":%d,"end":%d,"style":"%s"}],"emphasisRanges":[]}
                """.formatted(start, end, style);
    }

    private String malformedPayload() {
        return "{\"paragraphs\":{},\"emphasisRanges\":[]}";
    }
}
