package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultManagementQueryIT extends ActivityResultReadTestSupport {
    private static final String DETAIL_PATH = "/api/v1/activities/{activityId}/result";
    private static final String LIST_PATH = "/api/v1/school-admin/activity-results";

    @Test
    void read13ManagementDetailReturnsThreeExactPointerProjectionsIncludingBlockedHistory() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "management-detail", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", true, 3, 2, 1);

        mvc.perform(get(DETAIL_PATH, fixture.activityId()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCandidateVersionId").value(fixture.v3().toString()))
                .andExpect(jsonPath("$.currentInternalVersionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.currentPublicVersionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.publicVisibilityBlocked").value(true))
                .andExpect(jsonPath("$.candidateProjection.versionId").value(fixture.v3().toString()))
                .andExpect(jsonPath("$.internalProjection.versionId").value(fixture.v2().toString()))
                .andExpect(jsonPath("$.publicProjection.versionId").value(fixture.v1().toString()))
                .andExpect(jsonPath("$.candidateProjection.mediaRefs[0]").isNotEmpty());
    }

    @Test
    void read14AndRead38CrossSchoolManagementDetailUsesAntiEnumeration404() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "cross-school", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);

        mvc.perform(get(DETAIL_PATH, fixture.activityId()).with(adminB()))
                .andExpect(status().isNotFound());
    }

    @Test
    void read33ListIsDerivedFromAuthenticatedSchoolAndSupportsFrozenFilters() throws Exception {
        Fixture aDraft = insertResult(
                schoolA, "Alpha Result", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);
        insertResult(
                schoolA, "Beta Result", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        insertResult(
                schoolB, "Alpha Other School", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);

        mvc.perform(get(LIST_PATH)
                        .param("internalStatus", "DRAFT")
                        .param("publicStatus", "NOT_SUBMITTED")
                        .param("q", "  alpha  ")
                        .with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].resultId").value(aDraft.resultId().toString()))
                .andExpect(jsonPath("$.items[0].activityTitle").value("Alpha Result"));
    }

    @Test
    void read34ListExcludesActivitiesWithoutResultAndDoesNotCreateThem() throws Exception {
        UUID missingResultActivity = insertActivityWithoutResult(schoolA, "No Result", "PUBLISHED");
        Fixture existing = insertResult(
                schoolA, "Has Result", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM activity_results", Integer.class);

        mvc.perform(get(LIST_PATH).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].activityId").value(existing.activityId().toString()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_results", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_results WHERE activity_id = ?",
                Integer.class,
                missingResultActivity)).isZero();
    }

    @Test
    void read35ListPaginationAndOrderingAreDeterministic() throws Exception {
        Fixture older = insertResult(
                schoolA, "Older", "PUBLISHED", "DRAFT", "NOT_SUBMITTED", false, 1, null, null);
        Fixture newer = insertResult(
                schoolA, "Newer", "PUBLISHED", "DRAFT", "NOT_SUBMITTED", false, 1, null, null);
        setUpdatedAt(older.resultId(), Instant.parse("2026-01-01T00:00:00Z"));
        setUpdatedAt(newer.resultId(), Instant.parse("2026-01-02T00:00:00Z"));

        mvc.perform(get(LIST_PATH).param("page", "0").param("size", "1").with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(newer.resultId().toString()))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(LIST_PATH).param("page", "1").param("size", "1").with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(older.resultId().toString()));
    }

    @Test
    void read36ListProjectionDoesNotLeakContentOrHistory() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "No Leak", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_REJECTED", false, 2, 2, 1);
        appendReview(fixture, "REJECTED", fixture.v2(), "private reason", Instant.now());

        mvc.perform(get(LIST_PATH).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].activityTitle").value("No Leak"))
                .andExpect(jsonPath("$.items[0].title").doesNotExist())
                .andExpect(jsonPath("$.items[0].summaryText").doesNotExist())
                .andExpect(jsonPath("$.items[0].scoreHighlights").doesNotExist())
                .andExpect(jsonPath("$.items[0].mediaRefs").doesNotExist())
                .andExpect(jsonPath("$.items[0].history").doesNotExist())
                .andExpect(jsonPath("$.items[0].reason").doesNotExist());
    }

    @Test
    void read37MissingResultDetailReturnsEmptyEditorWithoutCreatingRows() throws Exception {
        UUID activityId = insertActivityWithoutResult(schoolA, "Empty Editor", "PUBLISHED");
        Integer resultsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM activity_results", Integer.class);
        Integer versionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM result_versions", Integer.class);

        mvc.perform(get(DETAIL_PATH, activityId).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()))
                .andExpect(jsonPath("$.resultId").doesNotExist())
                .andExpect(jsonPath("$.internalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publicStatus").value("NOT_SUBMITTED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_results", Integer.class))
                .isEqualTo(resultsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM result_versions", Integer.class))
                .isEqualTo(versionsBefore);
    }

    @Test
    @Transactional
    void brokenManagementPointerReturnsControlled409() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "broken-management", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);
        jdbc.execute("SET LOCAL session_replication_role = replica");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = ? WHERE id = ?",
                UUID.randomUUID(), fixture.resultId());
        jdbc.execute("SET LOCAL session_replication_role = origin");

        mvc.perform(get(DETAIL_PATH, fixture.activityId()).with(adminA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_RESULT_DATA_CONSISTENCY"));
    }
}
