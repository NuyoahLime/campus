package com.campusguinness.achievement.internal.persistence;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import com.campusguinness.achievement.application.query.model.AchievementIssueResult;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementIssuanceConcurrencyIT
        extends AchievementIntegrationTestSupport {

    @Test
    void concurrentIssueCreatesSingleRecord() throws Exception {
        RankingVersionDetail version = publishRanking();

        List<AchievementIssueResult> results =
                issueConcurrently(firstEntryId(version));

        assertThat(results).hasSize(2);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM achievement_records WHERE ranking_entry_id=?",
                        Long.class,
                        firstEntryId(version)))
                .isEqualTo(1);
    }

    @Test
    void concurrentIssueReturnsSameRecordIdAndVerificationCode()
            throws Exception {
        RankingVersionDetail version = publishRanking();

        List<AchievementIssueResult> results =
                issueConcurrently(firstEntryId(version));

        assertThat(results)
                .extracting(result -> result.record().recordId())
                .containsOnly(results.getFirst().record().recordId());
        assertThat(results)
                .extracting(result -> result.record().verificationCode())
                .containsOnly(results.getFirst().record().verificationCode());
        assertThat(results).filteredOn(AchievementIssueResult::created)
                .hasSize(1);
    }

    @Test
    void differentEntriesCreateDifferentRecordsAndCodes()
            throws Exception {
        UUID secondStudent = createUser(
                "ranking-second-student-" + fixtureSuffix);
        UUID secondMembership = membership(
                secondStudent, schoolId, "STUDENT", "ACTIVE");
        assignStudent(
                activityId,
                activityProjectId,
                secondMembership,
                adminId);
        createScore(
                activityProjectId,
                schoolId,
                secondStudent,
                teacherId,
                "INTEGER",
                new BigDecimal("90"),
                null,
                null,
                "APPROVED",
                true,
                Instant.parse("2026-07-30T09:00:00Z"));
        RankingVersionDetail version = publishRanking();
        UUID first = version.entries().get(0).rankingEntryId();
        UUID second = version.entries().get(1).rankingEntryId();

        List<AchievementIssueResult> results = runConcurrently(List.of(
                () -> achievementService.issue(adminId, first),
                () -> achievementService.issue(adminId, second)));

        assertThat(results).extracting(result -> result.record().recordId())
                .doesNotHaveDuplicates();
        assertThat(results)
                .extracting(result -> result.record().verificationCode())
                .doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM achievement_records",
                Long.class)).isEqualTo(2);
    }

    private List<AchievementIssueResult> issueConcurrently(
            UUID rankingEntryId) throws Exception {
        return runConcurrently(List.of(
                () -> achievementService.issue(adminId, rankingEntryId),
                () -> achievementService.issue(otherActiveAdmin(), rankingEntryId)));
    }

    private UUID otherActiveAdmin() {
        UUID actor = createUser("ranking-second-admin-" + fixtureSuffix);
        membership(actor, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        return actor;
    }

    private static <T> List<T> runConcurrently(
            List<Callable<T>> operations) throws Exception {
        ExecutorService executor =
                Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent start timed out");
                    }
                    return operation.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
