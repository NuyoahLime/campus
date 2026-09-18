package com.campusguinness.result.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewAction;
import com.campusguinness.result.internal.domain.ResultVersionId;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

class ActivityResultSliceD2AIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ActivityResultGovernanceApplicationService governanceService;
    @Autowired private ActivityResultPublicationApplicationService publicationService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ActivityResultRepository activityResults;
    @MockitoSpyBean private ResultVersionRepository resultVersions;
    @MockitoSpyBean private ResultReviewRecordRepository reviewRecords;

    private final String prefix = "slice-d2a-" + UUID.randomUUID();
    private UUID schoolId;
    private UUID schoolAdmin;
    private UUID student;
    private UUID superAdmin;

    @BeforeEach
    void setUp() {
        schoolId = insertSchool();
        schoolAdmin = insertUser("admin", null);
        student = insertUser("student", null);
        superAdmin = insertUser("super", "SUPER_ADMIN");
        insertMembership(schoolAdmin, "SCHOOL_ADMIN");
        insertMembership(student, "STUDENT");
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
    }

    @AfterEach
    void tearDown() {
        reset(activityResults, resultVersions, reviewRecords);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM result_review_records WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id = ?)", schoolId);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL WHERE school_id = ?",
                schoolId);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM activity_results WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM activities WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?, ?)", schoolAdmin, student);
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", schoolAdmin, student, superAdmin);
    }

    @Test
    void allVisiblePublicBaseStatusesTakedownExactPublicVersionAndPreserveHistory() {
        for (String status : List.of(
                "PUBLIC", "NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED",
                "PLATFORM_REJECTED", "ANOMALY_PENDING")) {
            Fixture fixture = visibleFixture(status, "PUBLISHED");
            if (!status.equals("PUBLIC") && !status.equals("ANOMALY_PENDING")) {
                appendSubmitted(fixture);
            }
            int historyBefore = historyCount(fixture.resultId());
            Instant publicStamp = publicStamp(fixture.publicVersionId());
            String publicTitle = versionTitle(fixture.publicVersionId());
            String candidateTitle = fixture.candidateVersionId() == null
                    ? null : versionTitle(fixture.candidateVersionId());

            Instant before = Instant.now();
            var response = governanceService.takedown(fixture.resultId(), "  emergency " + status + "  ");
            Instant after = Instant.now();

            assertThat(response.publicStatus()).isEqualTo("PLATFORM_TAKEDOWN");
            assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                    .isEqualTo(fixture.publicVersionId());
            assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
            assertThat(resultColumn(fixture.resultId(), "current_internal_version_id"))
                    .isEqualTo(fixture.internalVersionId());
            assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
            assertThat(publicStamp(fixture.publicVersionId())).isEqualTo(publicStamp);
            assertThat(versionTitle(fixture.publicVersionId())).isEqualTo(publicTitle);
            if (fixture.candidateVersionId() != null) {
                assertThat(versionTitle(fixture.candidateVersionId())).isEqualTo(candidateTitle);
            }
            assertThat(historyCount(fixture.resultId())).isEqualTo(historyBefore + 1);
            assertTakedownHistory(fixture, "emergency " + status, before, after);
        }
    }

    @Test
    void missingBlockedDuplicateAndForeignPublicVersionFailClosed() {
        Fixture missingPointer = visibleFixture("PUBLIC", "PUBLISHED");
        jdbc.update("UPDATE activity_results SET current_public_version_id = NULL WHERE id = ?",
                missingPointer.resultId());
        assertThatThrownBy(() -> governanceService.takedown(missingPointer.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentPublicVersionId");

        Fixture blocked = visibleFixture("PUBLIC", "PUBLISHED");
        jdbc.update("UPDATE activity_results SET public_visibility_blocked = true WHERE id = ?",
                blocked.resultId());
        assertThatThrownBy(() -> governanceService.takedown(blocked.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already blocked");

        Fixture duplicate = visibleFixture("PUBLIC", "PUBLISHED");
        governanceService.takedown(duplicate.resultId(), "first");
        assertThatThrownBy(() -> governanceService.takedown(duplicate.resultId(), "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already blocked");
        assertThat(takedownCount(duplicate.resultId())).isOne();

        Fixture missingVersion = visibleFixture("PUBLIC", "PUBLISHED");
        doReturn(Optional.empty()).when(resultVersions)
                .findById(new ResultVersionId(missingVersion.publicVersionId()));
        assertThatThrownBy(() -> governanceService.takedown(missingVersion.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");

        reset(resultVersions);
        Fixture target = visibleFixture("PUBLIC", "PUBLISHED");
        Fixture foreign = visibleFixture("PUBLIC", "PUBLISHED");
        var foreignVersion = resultVersions.findById(
                new ResultVersionId(foreign.publicVersionId())).orElseThrow();
        doReturn(Optional.of(foreignVersion)).when(resultVersions)
                .findById(new ResultVersionId(target.publicVersionId()));
        assertThatThrownBy(() -> governanceService.takedown(target.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");

        assertThat(resultStatus(missingPointer.resultId())).isEqualTo("PUBLIC");
        assertThat(resultStatus(missingVersion.resultId())).isEqualTo("PUBLIC");
        assertThat(resultStatus(target.resultId())).isEqualTo("PUBLIC");
        assertThat(takedownCount(target.resultId())).isZero();
    }

    @Test
    void reasonPolicyAndAuthorizationAreEnforced() {
        Fixture fixture = visibleFixture("PUBLIC", "PUBLISHED");
        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason required");
        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason required");
        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max 2000");

        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), "reason"))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(student, "STUDENT", schoolId, "STUDENT");
        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), "reason"))
                .isInstanceOf(IdentityApplicationException.class);

        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        governanceService.takedown(fixture.resultId(), "x".repeat(2000));
        assertThat(historyReason(fixture.resultId())).hasSize(2000);
    }

    @Test
    void cancelledActivityStillAllowsEmergencyTakedown() {
        Fixture fixture = visibleFixture("PUBLIC", "CANCELLED");

        governanceService.takedown(fixture.resultId(), "cancelled activity safety action");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(takedownCount(fixture.resultId())).isOne();
    }

    @Test
    void historyAppendFailureRollsBackVisibilityAndCandidateChanges() {
        Fixture fixture = visibleFixture("PENDING_PUBLIC_REVIEW", "PUBLISHED");
        appendSubmitted(fixture);
        doThrow(new IllegalStateException("history unavailable")).when(reviewRecords)
                .append(argThat(record -> record.action() == ResultReviewAction.TAKEDOWN));

        assertThatThrownBy(() -> governanceService.takedown(fixture.resultId(), "rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history unavailable");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id"))
                .isEqualTo(fixture.candidateVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(false);
        assertThat(takedownCount(fixture.resultId())).isZero();
    }

    @Test
    void concurrentDoubleTakedownHasExactlyOneSuccessAndOneHistoryRecord() throws Exception {
        Fixture fixture = visibleFixture("PUBLIC", "PUBLISHED");
        CyclicBarrier loaded = barrierOnAggregateLoad();

        RaceResult race = runRace(
                () -> takedownInThread(fixture.resultId(), "first"),
                () -> takedownInThread(fixture.resultId(), "second"));

        assertExactlyOneSuccess(race);
        assertThat(loaded.getNumberWaiting()).isZero();
        assertThat(isConcurrencyOrStateConflict(race.failure())).isTrue();
        assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(takedownCount(fixture.resultId())).isOne();
    }

    @Test
    void takedownAndMakePublicRaceHasOneAuthoritativeValidOutcome() throws Exception {
        Fixture fixture = visibleFixture("PLATFORM_APPROVED", "PUBLISHED");
        appendSubmitted(fixture);
        appendApproved(fixture);
        barrierOnAggregateLoad();

        RaceResult race = runRace(
                () -> takedownInThread(fixture.resultId(), "race"),
                () -> makePublicInThread(fixture.resultId()));

        assertExactlyOneSuccess(race);
        assertThat(isConcurrencyOrStateConflict(race.failure())).isTrue();
        String status = resultStatus(fixture.resultId());
        if (status.equals("PUBLIC")) {
            assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                    .isEqualTo(fixture.candidateVersionId());
            assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(false);
            assertThat(publicStamp(fixture.candidateVersionId())).isNotNull();
            assertThat(takedownCount(fixture.resultId())).isZero();
        } else {
            assertThat(status).isEqualTo("PLATFORM_TAKEDOWN");
            assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                    .isEqualTo(fixture.publicVersionId());
            assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
            assertThat(publicStamp(fixture.candidateVersionId())).isNull();
            assertThat(takedownCount(fixture.resultId())).isOne();
        }
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
    }

    private CyclicBarrier barrierOnAggregateLoad() throws Exception {
        CyclicBarrier loaded = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            loaded.await(10, TimeUnit.SECONDS);
            return value;
        }).when(activityResults).findById(any(ActivityResultId.class));
        return loaded;
    }

    private RaceResult runRace(ThrowingOperation firstOperation, ThrowingOperation secondOperation)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = executor.submit(() -> execute(ready, start, firstOperation));
            Future<Throwable> second = executor.submit(() -> execute(ready, start, secondOperation));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return new RaceResult(
                    first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable execute(
            CountDownLatch ready, CountDownLatch start, ThrowingOperation operation) {
        try {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void takedownInThread(UUID resultId, String reason) {
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        governanceService.takedown(resultId, reason);
    }

    private void makePublicInThread(UUID resultId) {
        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        publicationService.makePublic(resultId);
    }

    private static void assertExactlyOneSuccess(RaceResult race) {
        int successes = (race.firstFailure() == null ? 1 : 0) + (race.secondFailure() == null ? 1 : 0);
        assertThat(successes).isOne();
        assertThat(race.failure()).isNotNull();
    }

    private Fixture visibleFixture(String publicStatus, String executionStatus) {
        UUID activityId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID publicVersionId = UUID.randomUUID();
        boolean replacement = !publicStatus.equals("PUBLIC") && !publicStatus.equals("ANOMALY_PENDING");
        UUID candidateVersionId = replacement ? UUID.randomUUID() : null;
        UUID internalVersionId = replacement ? candidateVersionId : publicVersionId;
        Instant publicAt = Instant.parse("2026-09-18T08:00:00Z");
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, ?, 'NOT_SUBMITTED', ?)
                """, activityId, schoolId, prefix + "-activity-" + activityId, executionStatus, schoolAdmin);
        jdbc.update("""
                INSERT INTO activity_results(
                    id, school_id, activity_id, result_internal_status, result_public_status)
                VALUES (?, ?, ?, 'INTERNAL_PUBLISHED', ?)
                """, resultId, schoolId, activityId, publicStatus);
        insertVersion(publicVersionId, resultId, 1, "Public V1", publicAt);
        if (replacement) {
            insertVersion(candidateVersionId, resultId, 2, "Candidate V2", null);
        }
        jdbc.update("""
                UPDATE activity_results
                SET current_candidate_version_id = ?, current_internal_version_id = ?,
                    current_public_version_id = ?, public_visibility_blocked = false
                WHERE id = ?
                """, candidateVersionId, internalVersionId, publicVersionId, resultId);
        return new Fixture(activityId, resultId, publicVersionId, candidateVersionId, internalVersionId);
    }

    private void insertVersion(
            UUID versionId, UUID resultId, int number, String title, Instant publiclyAt) {
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights, media_refs,
                    published_internally_at, published_publicly_at)
                VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb, now(), ?)
                """, versionId, resultId, number, title, "Summary " + title,
                publiclyAt == null ? null : Timestamp.from(publiclyAt));
    }

    private void appendSubmitted(Fixture fixture) {
        if (fixture.candidateVersionId() == null) return;
        jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action, submitted_by, submitted_at, created_at)
                VALUES (?, ?, ?, 'SUBMITTED', ?, now(), now())
                """, UUID.randomUUID(), fixture.resultId(), fixture.candidateVersionId(), schoolAdmin);
    }

    private void appendApproved(Fixture fixture) {
        jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action, reviewer_id, reviewed_at, created_at)
                VALUES (?, ?, ?, 'APPROVED', ?, now(), now())
                """, UUID.randomUUID(), fixture.resultId(), fixture.candidateVersionId(), superAdmin);
    }

    private void assertTakedownHistory(
            Fixture fixture, String reason, Instant before, Instant after) {
        var row = jdbc.queryForMap("""
                SELECT result_id, result_version_id, reviewer_id, reviewed_at, reason,
                       submitted_by, submitted_at
                FROM result_review_records
                WHERE result_id = ? AND action = 'TAKEDOWN'
                """, fixture.resultId());
        assertThat(row.get("result_id")).isEqualTo(fixture.resultId());
        assertThat(row.get("result_version_id")).isEqualTo(fixture.publicVersionId());
        assertThat(row.get("reviewer_id")).isEqualTo(superAdmin);
        assertThat(((Timestamp) row.get("reviewed_at")).toInstant()).isBetween(before, after);
        assertThat(row.get("reason")).isEqualTo(reason);
        assertThat(row.get("submitted_by")).isNull();
        assertThat(row.get("submitted_at")).isNull();
    }

    private UUID insertSchool() {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix, "USCC", prefix.substring(0, 8) + suffix + "u",
                prefix.substring(0, 8) + suffix + "i", "PRIMARY", "Beijing",
                "Address", "Contact", "13800000000", prefix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role)
                VALUES (?, ?, '{noop}password', 'NORMAL', ?)
                """, id, prefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8),
                platformRole);
        return id;
    }

    private void insertMembership(UUID userId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private void authenticate(UUID userId, String role, UUID actorSchoolId, String membershipRole) {
        List<AuthenticatedSchoolMembership> memberships = actorSchoolId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(
                        UUID.randomUUID(), actorSchoolId, membershipRole));
        var details = new CampusGuinnessUserDetails(
                userId, prefix + "-principal", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    private String resultStatus(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT result_public_status FROM activity_results WHERE id = ?", String.class, resultId);
    }

    private Object resultColumn(UUID resultId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM activity_results WHERE id = ?", Object.class, resultId);
    }

    private Instant publicStamp(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT published_publicly_at FROM result_versions WHERE id = ?", Instant.class, versionId);
    }

    private String versionTitle(UUID versionId) {
        return jdbc.queryForObject("SELECT title FROM result_versions WHERE id = ?", String.class, versionId);
    }

    private int historyCount(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM result_review_records WHERE result_id = ?", Integer.class, resultId);
    }

    private int takedownCount(UUID resultId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM result_review_records WHERE result_id = ? AND action = 'TAKEDOWN'
                """, Integer.class, resultId);
    }

    private String historyReason(UUID resultId) {
        return jdbc.queryForObject("""
                SELECT reason FROM result_review_records WHERE result_id = ? AND action = 'TAKEDOWN'
                """, String.class, resultId);
    }

    private boolean isConcurrencyOrStateConflict(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && (current.getMessage().contains("already blocked")
                    || current.getMessage().contains("already published publicly")
                    || current.getMessage().contains("current candidate"))) {
                return true;
            }
            if (current.getMessage() != null
                    && (current.getMessage().contains("Cannot platform takedown from public status")
                    || current.getMessage().contains("Cannot make public from public status"))) {
                return true;
            }
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private record RaceResult(Throwable firstFailure, Throwable secondFailure) {
        private Throwable failure() {
            return firstFailure != null ? firstFailure : secondFailure;
        }
    }

    private record Fixture(
            UUID activityId,
            UUID resultId,
            UUID publicVersionId,
            UUID candidateVersionId,
            UUID internalVersionId) {
    }
}
