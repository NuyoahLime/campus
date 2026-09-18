package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultHistoryQueryIT extends ActivityResultReadTestSupport {
    private static final String HISTORY_PATH =
            "/api/v1/school-admin/activities/{activityId}/result/history";

    @Test
    void read39AndRead41SameSchoolHistoryIsCompleteReasonedAndChronological() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "history", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", true, null, 2, 1);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        appendReview(fixture, "SUBMITTED", fixture.v2(), null, base);
        appendReview(fixture, "APPROVED", fixture.v2(), null, base.plus(1, ChronoUnit.MINUTES));
        appendReview(fixture, "REJECTED", fixture.v2(), "rejection reason", base.plus(2, ChronoUnit.MINUTES));
        appendReview(fixture, "TAKEDOWN", fixture.v1(), "takedown reason", base.plus(3, ChronoUnit.MINUTES));
        appendReview(fixture, "RESET", fixture.v1(), "reset reason", base.plus(4, ChronoUnit.MINUTES));

        mvc.perform(get(HISTORY_PATH, fixture.activityId()).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].action").value("APPROVED"))
                .andExpect(jsonPath("$[2].action").value("REJECTED"))
                .andExpect(jsonPath("$[2].reason").value("rejection reason"))
                .andExpect(jsonPath("$[3].action").value("TAKEDOWN"))
                .andExpect(jsonPath("$[3].reason").value("takedown reason"))
                .andExpect(jsonPath("$[4].action").value("RESET"))
                .andExpect(jsonPath("$[4].reason").value("reset reason"))
                .andExpect(jsonPath("$[4].actorId").value(superAdmin.toString()));
    }

    @Test
    void read40CrossSchoolHistoryIs404() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "history-cross-school", "PUBLISHED", "INTERNAL_PUBLISHED",
                "NOT_SUBMITTED", false, 1, 1, null);

        mvc.perform(get(HISTORY_PATH, fixture.activityId()).with(adminB()))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingResultHistoryIsEmptyAndDoesNotCreateResult() throws Exception {
        var activityId = insertActivityWithoutResult(schoolA, "history-empty", "PUBLISHED");

        mvc.perform(get(HISTORY_PATH, activityId).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
