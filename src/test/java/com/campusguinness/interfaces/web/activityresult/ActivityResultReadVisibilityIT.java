package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultReadVisibilityIT extends ActivityResultReadTestSupport {
    private static final String PUBLIC_PATH = "/api/v1/public/activities/{activityId}/result";

    @Test
    void read01FirstPublicationStatesStayInvisible() throws Exception {
        for (String status : new String[]{"NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED"}) {
            Fixture fixture = insertResult(
                    schoolA, "first-" + status, "PUBLISHED", "INTERNAL_PUBLISHED",
                    status, false, 1, 1, null);
            mvc.perform(get(PUBLIC_PATH, fixture.activityId())).andExpect(status().isNotFound());
        }
    }

    @Test
    void read02To06ReplacementStatesReturnExactOldPublicV1() throws Exception {
        for (String status : new String[]{
                "NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED", "PLATFORM_REJECTED"}) {
            Fixture fixture = insertResult(
                    schoolA, "replacement-" + status, "PUBLISHED", "INTERNAL_PUBLISHED",
                    status, false, 2, 2, 1);
            mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()))
                    .andExpect(jsonPath("$.title").value("V1 replacement-" + status));
        }
    }

    @Test
    void read07PublicationPointerSwitchReturnsCompleteV2() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "switch", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_APPROVED", false, 2, 2, 1);

        mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.summaryText").value("Summary V1 switch"));

        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = 'PUBLIC', current_public_version_id = ?,
                    current_candidate_version_id = NULL, public_visibility_blocked = false
                WHERE id = ?
                """, fixture.v2(), fixture.resultId());

        mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.title").value("V2 switch"))
                .andExpect(jsonPath("$.summaryText").value("Summary V2 switch"))
                .andExpect(jsonPath("$.scoreHighlights[0]").value("highlight 2"));
    }

    @Test
    void read08Read09AndRead17GovernanceStatesFailClosed() throws Exception {
        Fixture takedown = insertResult(
                schoolA, "takedown", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_TAKEDOWN", true, null, 1, 1);
        Fixture reset = insertResult(
                schoolA, "reset", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", true, null, 1, 1);
        Fixture anomaly = insertResult(
                schoolA, "anomaly", "PUBLISHED", "INTERNAL_PUBLISHED",
                "ANOMALY_PENDING", false, null, 1, 1);

        for (Fixture fixture : new Fixture[]{takedown, reset, anomaly}) {
            mvc.perform(get(PUBLIC_PATH, fixture.activityId())).andExpect(status().isNotFound());
        }
    }

    @Test
    void read10AndRead11CandidateAndInternalPointersNeverBecomePublicAuthority() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "not-public", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 3, 2, null);

        mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void read12BrokenPublicPointerFailsClosedWithoutFallback() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-public", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        UUID missing = UUID.randomUUID();
        jdbc.execute("SET LOCAL session_replication_role = replica");
        jdbc.update("UPDATE activity_results SET current_public_version_id = ? WHERE id = ?",
                missing, fixture.resultId());
        jdbc.execute("SET LOCAL session_replication_role = origin");

        mvc.perform(get(PUBLIC_PATH, fixture.activityId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void read15Read16AndRead22PublicProjectionIsMinimalAndCancelledRemainsReadable() throws Exception {
        Fixture cancelled = insertResult(
                schoolA, "cancelled", "CANCELLED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        Fixture draft = insertResult(
                schoolA, "draft-activity", "DRAFT", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);

        mvc.perform(get(PUBLIC_PATH, cancelled.activityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(cancelled.v1().toString()))
                .andExpect(jsonPath("$.mediaRefs").doesNotExist())
                .andExpect(jsonPath("$.currentCandidateVersionId").doesNotExist())
                .andExpect(jsonPath("$.currentInternalVersionId").doesNotExist())
                .andExpect(jsonPath("$.publicStatus").doesNotExist())
                .andExpect(jsonPath("$.publicVisibilityBlocked").doesNotExist());
        mvc.perform(get(PUBLIC_PATH, draft.activityId())).andExpect(status().isNotFound());
    }
}
