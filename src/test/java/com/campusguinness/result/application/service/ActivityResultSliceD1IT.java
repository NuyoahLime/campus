package com.campusguinness.result.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

class ActivityResultSliceD1IT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ActivityResultApplicationService editorService;
    @Autowired private ActivityResultReviewApplicationService reviewService;
    @Autowired private ActivityResultPublicationApplicationService publicationService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ActivityResultRepository activityResults;
    @MockitoSpyBean private ResultVersionRepository resultVersions;

    private final String prefix = "slice-d1-" + UUID.randomUUID();
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;
    private UUID superAdmin;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        superAdmin = insertUser("super", "SUPER_ADMIN");
        insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        insertMembership(studentA, schoolA, "STUDENT");
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
    }

    @AfterEach
    void tearDown() {
        reset(activityResults, resultVersions);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM result_review_records WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL "
                + "WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("DELETE FROM activity_results WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?, ?, ?)", adminA, adminB, studentA);
        jdbc.update("DELETE FROM schools WHERE id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", adminA, adminB, studentA, superAdmin);
    }

    @Test
    void firstPublicationSwitchesExactPointersAndStampsApprovedCandidate() {
        Fixture fixture = approvedFixture("First V1", "PUBLISHED");
        jdbc.update("UPDATE activity_results SET public_visibility_blocked = true WHERE id = ?",
                fixture.resultId());
        int historyBefore = historyCount(fixture.resultId());
        Instant before = Instant.now();

        var response = publicationService.makePublic(fixture.resultId());

        Instant after = Instant.now();
        assertThat(response.publicStatus()).isEqualTo("PUBLIC");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "current_internal_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(false);
        assertThat(publicStamp(fixture.versionId())).isBetween(before, after);
        assertThat(historyCount(fixture.resultId())).isEqualTo(historyBefore);
    }

    @Test
    void replacementPublicationPreservesOldVersionTimestampAndReviewHistory() {
        Fixture first = approvedFixture("Public V1", "PUBLISHED");
        publicationService.makePublic(first.resultId());
        Instant oldStamp = publicStamp(first.versionId());
        List<String> oldHistory = historyActions(first.resultId(), first.versionId());

        var edited = editorService.saveEditorContent(
                first.activityId(), command("Replacement V2"));
        UUID replacementId = edited.currentCandidateVersionId();
        editorService.publishInternal(first.resultId());
        reviewService.submit(first.resultId());
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        reviewService.approve(first.resultId());
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");

        publicationService.makePublic(first.resultId());

        assertThat(resultColumn(first.resultId(), "current_public_version_id"))
                .isEqualTo(replacementId);
        assertThat(resultColumn(first.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(first.resultId(), "current_internal_version_id"))
                .isEqualTo(replacementId);
        assertThat(publicStamp(replacementId)).isNotNull();
        assertThat(publicStamp(first.versionId())).isEqualTo(oldStamp);
        assertThat(versionTitle(first.versionId())).isEqualTo("Public V1");
        assertThat(historyActions(first.resultId(), first.versionId())).isEqualTo(oldHistory);
    }

    @Test
    void onlyPlatformApprovedPublicStateCanPublish() {
        for (String state : List.of(
                "NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW", "PLATFORM_REJECTED",
                "PUBLIC", "PLATFORM_TAKEDOWN", "ANOMALY_PENDING")) {
            Fixture fixture = approvedFixture("Wrong " + state, "PUBLISHED");
            jdbc.update("UPDATE activity_results SET result_public_status = ? WHERE id = ?",
                    state, fixture.resultId());

            assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(publicStamp(fixture.versionId())).isNull();
            assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id"))
                    .isEqualTo(fixture.versionId());
        }
    }

    @Test
    void activityExecutionStateGateAllowsOnlyPublishedInProgressAndEnded() {
        for (String allowed : List.of("PUBLISHED", "IN_PROGRESS", "ENDED")) {
            Fixture fixture = approvedFixture("Allowed " + allowed, allowed);
            assertThat(publicationService.makePublic(fixture.resultId()).publicStatus())
                    .isEqualTo("PUBLIC");
        }
        for (String denied : List.of("DRAFT", "CANCELLED")) {
            Fixture fixture = approvedFixture("Denied " + denied, "PUBLISHED");
            jdbc.update("UPDATE activities SET execution_status = ? WHERE id = ?",
                    denied, fixture.activityId());
            assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("execution status " + denied);
            assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_APPROVED");
        }
    }

    @Test
    void publicationAuthorizationIsSameSchoolAdminOnly() {
        Fixture fixture = approvedFixture("Authorized", "PUBLISHED");

        authenticate(adminB, "SCHOOL_ADMIN", schoolB, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(studentA, "STUDENT", schoolA, "STUDENT");
        assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_APPROVED");

        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        assertThat(publicationService.makePublic(fixture.resultId()).publicStatus()).isEqualTo("PUBLIC");
    }

    @Test
    void nullCandidateStaleApprovalAndInternalMismatchFailClosed() {
        Fixture missing = approvedFixture("Missing", "PUBLISHED");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL WHERE id = ?",
                missing.resultId());
        assertThatThrownBy(() -> publicationService.makePublic(missing.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentCandidateVersionId");

        Fixture stale = approvedFixture("Stale V1", "PUBLISHED");
        UUID staleV2 = insertVersion(stale.resultId(), 2, "Stale V2");
        jdbc.update("""
                UPDATE activity_results
                SET current_candidate_version_id = ?, current_internal_version_id = ?
                WHERE id = ?
                """, staleV2, staleV2, stale.resultId());
        assertThatThrownBy(() -> publicationService.makePublic(stale.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact approved");

        Fixture mismatch = approvedFixture("Mismatch V1", "PUBLISHED");
        UUID mismatchV2 = insertVersion(mismatch.resultId(), 2, "Mismatch V2");
        jdbc.update("UPDATE activity_results SET current_internal_version_id = ? WHERE id = ?",
                mismatchV2, mismatch.resultId());
        assertThatThrownBy(() -> publicationService.makePublic(mismatch.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current internal version");

        assertThat(resultStatus(stale.resultId())).isEqualTo("PLATFORM_APPROVED");
        assertThat(publicStamp(staleV2)).isNull();
        assertThat(resultStatus(mismatch.resultId())).isEqualTo("PLATFORM_APPROVED");
    }

    @Test
    void candidateOwnershipIsValidatedEvenWhenRepositoryReturnsForeignVersion() {
        Fixture target = approvedFixture("Target", "PUBLISHED");
        Fixture foreign = approvedFixture("Foreign", "PUBLISHED");
        var foreignVersion = resultVersions.findById(new ResultVersionId(foreign.versionId())).orElseThrow();
        doReturn(Optional.of(foreignVersion)).when(resultVersions)
                .findById(new ResultVersionId(target.versionId()));

        assertThatThrownBy(() -> publicationService.makePublic(target.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
        assertThat(resultStatus(target.resultId())).isEqualTo("PLATFORM_APPROVED");
        assertThat(publicStamp(target.versionId())).isNull();
    }

    @Test
    void preexistingPublicTimestampFailureRollsBackAggregateTransition() {
        Fixture fixture = approvedFixture("Already stamped", "PUBLISHED");
        Instant originalStamp = Instant.parse("2026-09-17T13:00:00Z");
        jdbc.update("UPDATE result_versions SET published_publicly_at = ? WHERE id = ?",
                Timestamp.from(originalStamp), fixture.versionId());

        assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published publicly");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_APPROVED");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id")).isNull();
        assertThat(publicStamp(fixture.versionId())).isEqualTo(originalStamp);
    }

    @Test
    void concurrentPublicationHasExactlyOneSuccessAndValidFinalState() throws Exception {
        Fixture fixture = approvedFixture("Concurrent", "PUBLISHED");
        CyclicBarrier loaded = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            loaded.await(10, TimeUnit.SECONDS);
            return value;
        }).when(activityResults).findById(any(ActivityResultId.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Throwable firstFailure;
        Throwable secondFailure;
        try {
            Future<Throwable> first = executor.submit(
                    () -> publishInThread(fixture.resultId(), ready, start));
            Future<Throwable> second = executor.submit(
                    () -> publishInThread(fixture.resultId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFailure = first.get(20, TimeUnit.SECONDS);
            secondFailure = second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        int successes = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
        Throwable conflict = firstFailure == null ? secondFailure : firstFailure;
        assertThat(successes).isOne();
        assertThat(conflict).isNotNull();
        assertThat(isConcurrencyConflict(conflict)).isTrue();
        assertThat(resultStatus(fixture.resultId())).isEqualTo("PUBLIC");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(false);
        assertThat(publicStamp(fixture.versionId())).isNotNull();
        assertThat(historyActions(fixture.resultId(), fixture.versionId()))
                .containsExactly("SUBMITTED", "APPROVED");
    }

    private Fixture approvedFixture(String title, String executionStatus) {
        UUID activityId = insertActivity(schoolA, executionStatus);
        var saved = editorService.saveEditorContent(activityId, command(title));
        editorService.publishInternal(saved.resultId());
        reviewService.submit(saved.resultId());
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        reviewService.approve(saved.resultId());
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        return new Fixture(activityId, saved.resultId(), saved.currentCandidateVersionId());
    }

    private SaveActivityResultContentCommand command(String title) {
        return new SaveActivityResultContentCommand(title, "Summary " + title, List.of("score"), List.of());
    }

    private Throwable publishInThread(
            UUID resultId,
            CountDownLatch ready,
            CountDownLatch start) {
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        try {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            publicationService.makePublic(resultId);
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID insertActivity(UUID schoolId, String executionStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, ?, 'NOT_SUBMITTED', ?)
                """, id, schoolId, prefix + "-activity-" + id, executionStatus, adminA);
        return id;
    }

    private UUID insertVersion(UUID resultId, int number, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights,
                    media_refs, published_internally_at
                ) VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb, now())
                """, id, resultId, number, title, "Summary " + title);
        return id;
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix + "-" + label, "USCC", prefix.substring(0, 8) + suffix + "u",
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

    private void insertMembership(UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private void authenticate(UUID userId, String role, UUID schoolId, String membershipRole) {
        List<AuthenticatedSchoolMembership> memberships = schoolId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, membershipRole));
        var details = new CampusGuinnessUserDetails(
                userId,
                prefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships);
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
        return jdbc.queryForObject(
                "SELECT title FROM result_versions WHERE id = ?", String.class, versionId);
    }

    private int historyCount(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM result_review_records WHERE result_id = ?", Integer.class, resultId);
    }

    private List<String> historyActions(UUID resultId, UUID versionId) {
        return jdbc.queryForList("""
                SELECT action FROM result_review_records
                WHERE result_id = ? AND result_version_id = ?
                ORDER BY created_at ASC, id ASC
                """, String.class, resultId, versionId);
    }

    private boolean isConcurrencyConflict(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && (current.getMessage().contains("already published publicly")
                    || current.getMessage().contains("PLATFORM_APPROVED"))) {
                return true;
            }
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private record Fixture(UUID activityId, UUID resultId, UUID versionId) {
    }
}
