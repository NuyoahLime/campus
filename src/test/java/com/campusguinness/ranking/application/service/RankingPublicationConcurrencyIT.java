package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.exception.RankingConflictException;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPublicationConcurrencyIT extends RankingIntegrationTestSupport {

    @Autowired SchoolAdminRankingApplicationService service;

    @Test
    void concurrentFirstPublishCreatesSingleDefinition() throws Exception {
        publishConcurrently();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_definitions
                WHERE activity_project_id=?
                """, Long.class, activityProjectId)).isEqualTo(1);
    }

    @Test
    void concurrentPublishAllocatesSequentialVersionNumbers() throws Exception {
        List<RankingVersionDetail> versions = publishConcurrently();

        assertThat(versions).extracting(RankingVersionDetail::versionNumber)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(jdbc.queryForList("""
                SELECT version.version_number
                FROM ranking_versions version
                JOIN ranking_definitions definition
                  ON definition.id=version.definition_id
                WHERE definition.activity_project_id=?
                ORDER BY version.version_number
                """, Integer.class, activityProjectId))
                .containsExactly(1, 2);
    }

    @Test
    void concurrentPublishLeavesSingleCurrentVersion() throws Exception {
        publishConcurrently();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_versions version
                JOIN ranking_definitions definition
                  ON definition.id=version.definition_id
                WHERE definition.activity_project_id=?
                  AND version.version_status='PUBLISHED'
                  AND version.id=definition.current_version_id
                """, Long.class, activityProjectId)).isEqualTo(1);
    }

    @Test
    void concurrentPublishDoesNotCreateOrphanEntries() throws Exception {
        publishConcurrently();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_entries entry
                LEFT JOIN ranking_entry_score_sources source
                  ON source.entry_id=entry.id
                JOIN ranking_versions version ON version.id=entry.version_id
                JOIN ranking_definitions definition
                  ON definition.id=version.definition_id
                WHERE definition.activity_project_id=?
                  AND source.id IS NULL
                """, Long.class, activityProjectId)).isZero();
    }

    @Test
    void concurrentWithdrawOnlyOneSucceeds() throws Exception {
        publish();
        String reason = "concurrent correction";
        List<Attempt<Void>> attempts = runConcurrently(List.of(
                () -> {
                    service.withdraw(adminId, activityProjectId, reason);
                    return null;
                },
                () -> {
                    service.withdraw(adminId, activityProjectId, reason);
                    return null;
                }));

        assertThat(attempts).filteredOn(Attempt::success).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.success())
                .singleElement()
                .extracting(Attempt::failure)
                .isInstanceOf(RankingConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_versions version
                JOIN ranking_definitions definition
                  ON definition.id=version.definition_id
                WHERE definition.activity_project_id=?
                  AND version.version_status='WITHDRAWN'
                """, Long.class, activityProjectId)).isEqualTo(1);
    }

    @Test
    void publishAndWithdrawDoNotCorruptCurrentPointer() throws Exception {
        publish();
        String fingerprint = service.preview(
                adminId, activityProjectId).sourceFingerprint();
        runConcurrently(List.of(
                () -> service.publish(adminId, activityProjectId, fingerprint),
                () -> {
                    service.withdraw(adminId, activityProjectId, "correction");
                    return null;
                }));

        var rows = jdbc.queryForList("""
                SELECT definition.current_version_id,
                       version.version_status,
                       version.withdrawn_at
                FROM ranking_definitions definition
                LEFT JOIN ranking_versions version
                  ON version.id=definition.current_version_id
                WHERE definition.activity_project_id=?
                """, activityProjectId);
        assertThat(rows).hasSize(1);
        Object current = rows.getFirst().get("current_version_id");
        if (current != null) {
            assertThat(rows.getFirst().get("version_status")).isEqualTo("PUBLISHED");
            assertThat(rows.getFirst().get("withdrawn_at")).isNull();
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_definitions definition
                JOIN ranking_versions version
                  ON version.definition_id=definition.id
                LEFT JOIN ranking_entries entry
                  ON entry.version_id=version.id
                WHERE definition.activity_project_id=?
                  AND version.version_status='PUBLISHED'
                  AND entry.id IS NULL
                """, Long.class, activityProjectId)).isZero();
    }

    private List<RankingVersionDetail> publishConcurrently() throws Exception {
        String fingerprint = service.preview(
                adminId, activityProjectId).sourceFingerprint();
        List<Attempt<RankingVersionDetail>> attempts = runConcurrently(List.of(
                () -> service.publish(adminId, activityProjectId, fingerprint),
                () -> service.publish(adminId, activityProjectId, fingerprint)));
        assertThat(attempts).allMatch(Attempt::success);
        return attempts.stream().map(Attempt::value).toList();
    }

    private RankingVersionDetail publish() {
        String fingerprint = service.preview(
                adminId, activityProjectId).sourceFingerprint();
        return service.publish(adminId, activityProjectId, fingerprint);
    }

    private static <T> List<Attempt<T>> runConcurrently(
            List<Callable<T>> operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Attempt<T>>> futures = new ArrayList<>();
            for (Callable<T> operation : operations) {
                futures.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Concurrent start timed out");
                        }
                        try {
                            return Attempt.<T>passed(operation.call());
                        } catch (Throwable failure) {
                            return Attempt.<T>failed(unwrap(failure));
                        }
                    }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Attempt<T>> results = new ArrayList<>();
            for (Future<Attempt<T>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof ExecutionException execution
                && execution.getCause() != null) {
            return execution.getCause();
        }
        return failure;
    }

    private record Attempt<T>(T value, Throwable failure) {
        static <T> Attempt<T> passed(T value) {
            return new Attempt<>(value, null);
        }

        static <T> Attempt<T> failed(Throwable failure) {
            return new Attempt<>(null, failure);
        }

        boolean success() {
            return failure == null;
        }
    }
}
