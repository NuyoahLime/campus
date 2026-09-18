package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultReadConcurrencyIT extends ActivityResultReadTestSupport {
    @Test
    void read19MakePublicRaceReturnsCompleteOldOrNewPublicSnapshot() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "make-public-race", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_APPROVED", false, 2, 2, 1);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> read = executor.submit(awaitThen(() -> mvc.perform(
                    get("/api/v1/public/activities/{activityId}/result", fixture.activityId())).andReturn(), barrier));
            Future<?> publish = executor.submit(awaitThen(() -> {
                jdbc.update("""
                        UPDATE activity_results
                        SET result_public_status = 'PUBLIC', current_public_version_id = ?,
                            current_candidate_version_id = NULL, public_visibility_blocked = false
                        WHERE id = ?
                        """, fixture.v2(), fixture.resultId());
                return null;
            }, barrier));
            publish.get();
            MvcResult result = read.get();
            assertThat(result.getResponse().getStatus()).isIn(200, 404);
            if (result.getResponse().getStatus() == 200) {
                String body = result.getResponse().getContentAsString();
                assertThat(body).containsAnyOf(
                        fixture.v1().toString(), fixture.v2().toString());
                assertThat(body).doesNotContain("V1 make-public-race\".*V2 make-public-race");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void read20TakedownRaceReturnsCompletePublicSnapshotOr404NeverBlockedContent() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "takedown-race", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> read = executor.submit(awaitThen(() -> mvc.perform(
                    get("/api/v1/public/activities/{activityId}/result", fixture.activityId())).andReturn(), barrier));
            Future<?> takedown = executor.submit(awaitThen(() -> {
                jdbc.update("""
                        UPDATE activity_results
                        SET result_public_status = 'PLATFORM_TAKEDOWN',
                            current_candidate_version_id = NULL,
                            public_visibility_blocked = true
                        WHERE id = ?
                        """, fixture.resultId());
                return null;
            }, barrier));
            takedown.get();
            MvcResult result = read.get();
            assertThat(result.getResponse().getStatus()).isIn(200, 404);
            if (result.getResponse().getStatus() == 200) {
                assertThat(result.getResponse().getContentAsString())
                        .contains(fixture.v1().toString())
                        .doesNotContain("PLATFORM_TAKEDOWN")
                        .doesNotContain("publicVisibilityBlocked");
            }
            mvc.perform(get("/api/v1/public/activities/{activityId}/result", fixture.activityId()))
                    .andExpect(status().isNotFound());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void read21ResetRaceAndPostCommitReadAlwaysDenyWhileBlocked() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "reset-race", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PLATFORM_TAKEDOWN", true, null, 1, 1);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> read = executor.submit(awaitThen(() -> mvc.perform(
                    get("/api/v1/public/activities/{activityId}/result", fixture.activityId())).andReturn(), barrier));
            Future<?> reset = executor.submit(awaitThen(() -> {
                jdbc.update("""
                        UPDATE activity_results
                        SET result_public_status = 'NOT_SUBMITTED',
                            current_candidate_version_id = NULL,
                            public_visibility_blocked = true
                        WHERE id = ?
                        """, fixture.resultId());
                return null;
            }, barrier));
            reset.get();
            MvcResult result = read.get();
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            mvc.perform(get("/api/v1/public/activities/{activityId}/result", fixture.activityId()))
                    .andExpect(status().isNotFound());
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> Callable<T> awaitThen(Callable<T> action, CyclicBarrier barrier) {
        return () -> {
            barrier.await();
            return action.call();
        };
    }
}
