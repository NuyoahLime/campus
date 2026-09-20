package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultStudentReadIT extends ActivityResultReadTestSupport {
    private static final String STUDENT_PATH = "/api/v1/student/activities/{activityId}/result";
    private static final String PUBLIC_PATH = "/api/v1/public/activities/{activityId}/result";

    @Test
    void read23AndRead24InternalPublishedAllowsExactInternalButDraftCandidateIsDenied() throws Exception {
        Fixture draft = insertResult(
                schoolA, "draft", "PUBLISHED", "DRAFT", "NOT_SUBMITTED", false, 1, null, null);
        Fixture internal = insertResult(
                schoolA, "internal", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 1, 1, null);

        mvc.perform(get(STUDENT_PATH, draft.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get(STUDENT_PATH, internal.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(internal.v1().toString()))
                .andExpect(jsonPath("$.visibilitySource").value("INTERNAL"));
    }

    @Test
    void read25DraftReplacementUsesPublicV1AndNeverCandidateV2() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "draft-replacement", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 2, 1, 1);

        mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.visibilitySource").value("PUBLIC"));
    }

    @Test
    void read26InternalV2PrecedesPublicV1WhileAnonymousStillGetsV1() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "internal-replacement", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 2, 2, 1);

        mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.visibilitySource").value("INTERNAL"));
        mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()));
    }

    @Test
    void pendingApprovedAndRejectedReviewDoNotRevokeIndependentInternalV2() throws Exception {
        for (String status : new String[]{
                "PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED", "PLATFORM_REJECTED"}) {
            Fixture fixture = insertResult(
                    schoolA, "review-" + status, "PUBLISHED", "INTERNAL_PUBLISHED",
                    status, false, 2, 2, 1);
            mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()));
            mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()));
        }
    }

    @Test
    void read27AndRead28TakedownAndResetDenyHistoricalInternalPointer() throws Exception {
        Fixture takedown = insertResult(
                schoolA, "student-takedown", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_TAKEDOWN", true, null, 1, 1);
        Fixture reset = insertResult(
                schoolA, "student-reset", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", true, null, 1, 1);

        mvc.perform(get(STUDENT_PATH, takedown.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get(STUDENT_PATH, reset.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void read29AndRead30PostResetDraftDeniesButExactNewInternalV2Recovers() throws Exception {
        Fixture draft = insertResult(
                schoolA, "post-reset-draft", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", true, 2, 1, 1);
        Fixture published = insertResult(
                schoolA, "post-reset-published", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", true, 2, 2, 1);

        mvc.perform(get(STUDENT_PATH, draft.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get(STUDENT_PATH, published.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(published.v2().toString()))
                .andExpect(jsonPath("$.visibilitySource").value("INTERNAL"));
        mvc.perform(get(PUBLIC_PATH, published.activityId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void read31AndRead32WithdrawnAndAnomalyStatesDenyStudent() throws Exception {
        Fixture withdrawn = insertResult(
                schoolA, "withdrawn", "PUBLISHED", "INTERNAL_WITHDRAWN",
                "NOT_SUBMITTED", false, null, 1, null);
        Fixture anomaly = insertResult(
                schoolA, "student-anomaly", "PUBLISHED", "INTERNAL_PUBLISHED",
                "ANOMALY_PENDING", false, null, 1, 1);

        mvc.perform(get(STUDENT_PATH, withdrawn.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get(STUDENT_PATH, anomaly.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void read42CandidateDifferentFromInternalReturnsOnlyExactInternal() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "candidate-three", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 3, 2, null);

        mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.title").value("V2 candidate-three"));
    }

    @Test
    @Transactional
    void read43BrokenInternalAuthorityFailsClosedWithoutPublicFallback() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-internal", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        UUID missing = UUID.randomUUID();
        jdbc.execute("SET LOCAL session_replication_role = replica");
        jdbc.update("UPDATE activity_results SET current_internal_version_id = ? WHERE id = ?",
                missing, fixture.resultId());
        jdbc.execute("SET LOCAL session_replication_role = origin");

        mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelledActivityStillUsesStudentInternalAuthority() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "cancelled-internal", "CANCELLED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 1, 1, null);

        mvc.perform(get(STUDENT_PATH, fixture.activityId()).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()));
    }
}
