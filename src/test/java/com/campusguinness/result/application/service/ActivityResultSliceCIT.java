package com.campusguinness.result.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.query.ActivityResultReviewQueryService;
import com.campusguinness.result.application.query.ActivityResultGovernanceQueryService;
import com.campusguinness.result.internal.domain.ActivityResultId;
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
import java.util.List;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

class ActivityResultSliceCIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ActivityResultApplicationService editorService;
    @Autowired private ActivityResultReviewApplicationService reviewService;
    @Autowired private ActivityResultReviewQueryService reviewQueryService;
    @Autowired private ActivityResultGovernanceQueryService governanceQueryService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ResultReviewRecordRepository reviewRecords;
    @MockitoSpyBean private ActivityResultRepository activityResults;

    private final String prefix = "slice-c-" + UUID.randomUUID();
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;
    private UUID superAdminA;
    private UUID superAdminB;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        superAdminA = insertUser("super-a", "SUPER_ADMIN");
        superAdminB = insertUser("super-b", "SUPER_ADMIN");
        insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        insertMembership(studentA, schoolA, "STUDENT");
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
    }

    @AfterEach
    void tearDown() {
        reset(reviewRecords, activityResults);
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
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?)",
                adminA, adminB, studentA, superAdminA, superAdminB);
    }

    @Test
    void submitBindsExactPublishedCandidateAndAppendsSubmission() {
        ReviewFixture fixture = publishedFixture("V1");

        var result = reviewService.submit(fixture.resultId());

        assertThat(result.publicStatus()).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(historyActions(fixture.resultId(), fixture.versionId())).containsExactly("SUBMITTED");
        assertThat(jdbc.queryForObject("""
                SELECT submitted_by FROM result_review_records
                WHERE result_id = ? AND result_version_id = ? AND action = 'SUBMITTED'
                """, UUID.class, fixture.resultId(), fixture.versionId())).isEqualTo(adminA);
        assertThat(jdbc.queryForObject("""
                SELECT submitted_at IS NOT NULL FROM result_review_records
                WHERE result_id = ? AND result_version_id = ? AND action = 'SUBMITTED'
        """, Boolean.class, fixture.resultId(), fixture.versionId())).isTrue();
    }

    @Test
    void submitRejectsCancelledActivityBeforeChangingReviewState() {
        ReviewFixture fixture = publishedFixture("Cancelled submit");
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?", fixture.activityId());

        assertThatThrownBy(() -> reviewService.submit(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultPublicStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(historyCount(fixture.resultId())).isZero();
    }

    @Test
    void approveAndRejectRejectCancelledActivityWithValidOpenSubmission() {
        ReviewFixture approvedCandidate = publishedFixture("Cancelled approve");
        reviewService.submit(approvedCandidate.resultId());
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?",
                approvedCandidate.activityId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        assertThatThrownBy(() -> reviewService.approve(approvedCandidate.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultPublicStatus(approvedCandidate.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(historyActions(approvedCandidate.resultId(), approvedCandidate.versionId()))
                .containsExactly("SUBMITTED");

        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        ReviewFixture rejectedCandidate = publishedFixture("Cancelled reject");
        reviewService.submit(rejectedCandidate.resultId());
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?",
                rejectedCandidate.activityId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        assertThatThrownBy(() -> reviewService.reject(rejectedCandidate.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultPublicStatus(rejectedCandidate.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(historyActions(rejectedCandidate.resultId(), rejectedCandidate.versionId()))
                .containsExactly("SUBMITTED");
    }

    @Test
    void submitRejectsDraftMissingCandidateMismatchAndDuplicateWithoutExtraHistory() {
        UUID activityId = insertActivity(schoolA);
        var draft = editorService.saveEditorContent(activityId, command("Draft"));
        assertThatThrownBy(() -> reviewService.submit(draft.resultId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("internal status DRAFT");
        assertThat(historyCount(draft.resultId())).isZero();

        jdbc.update("""
                UPDATE activity_results
                SET result_internal_status = 'INTERNAL_PUBLISHED', current_candidate_version_id = NULL
                WHERE id = ?
                """, draft.resultId());
        assertThatThrownBy(() -> reviewService.submit(draft.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentCandidateVersionId");

        jdbc.update("""
                UPDATE activity_results
                SET current_candidate_version_id = ?, current_internal_version_id = ?
                WHERE id = ?
                """, draft.currentCandidateVersionId(), draft.currentCandidateVersionId(), draft.resultId());
        UUID v2 = insertVersion(draft.resultId(), 2, "V2");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = ? WHERE id = ?",
                v2, draft.resultId());
        assertThatThrownBy(() -> reviewService.submit(draft.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current internal version");

        jdbc.update("UPDATE activity_results SET current_candidate_version_id = ? WHERE id = ?",
                draft.currentCandidateVersionId(), draft.resultId());
        reviewService.submit(draft.resultId());
        assertThatThrownBy(() -> reviewService.submit(draft.resultId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(historyCount(draft.resultId())).isOne();
    }

    @Test
    void submitAuthorizationIsSameSchoolOnly() {
        ReviewFixture fixture = publishedFixture("V1");

        authenticate(adminB, "SCHOOL_ADMIN", schoolB, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> reviewService.submit(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(studentA, "STUDENT", schoolA, "STUDENT");
        assertThatThrownBy(() -> reviewService.submit(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        assertThatThrownBy(() -> reviewService.submit(fixture.resultId()))
                .isInstanceOf(IdentityApplicationException.class);
        assertThat(historyCount(fixture.resultId())).isZero();

        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        assertThat(reviewService.submit(fixture.resultId()).publicStatus())
                .isEqualTo("PENDING_PUBLIC_REVIEW");
    }

    @Test
    void submissionHistoryFailureRollsBackPendingTransition() {
        ReviewFixture fixture = publishedFixture("V1");
        doThrow(new IllegalStateException("history unavailable"))
                .when(reviewRecords).append(any());

        assertThatThrownBy(() -> reviewService.submit(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("history unavailable");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(historyCount(fixture.resultId())).isZero();
    }

    @Test
    void pendingListIsNarrowAndRequiresSuperAdmin() {
        ReviewFixture pending = submittedFixture("Pending");
        publishedFixture("Not submitted");
        ReviewFixture approved = submittedFixture("Approved");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        reviewService.approve(approved.resultId());

        var page = reviewQueryService.listPending(0, 20);

        assertThat(page.items()).extracting(item -> item.resultId())
                .containsExactly(pending.resultId());
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> reviewQueryService.listPending(0, 20))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(studentA, "STUDENT", schoolA, "STUDENT");
        assertThatThrownBy(() -> reviewQueryService.listPending(0, 20))
                .isInstanceOf(IdentityApplicationException.class);
    }

    @Test
    void pendingDetailCannotBrowseReviewedOrNonPendingResults() {
        ReviewFixture approved = submittedFixture("Approved");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        reviewService.approve(approved.resultId());

        assertThatThrownBy(() -> reviewQueryService.pendingDetail(approved.resultId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void pendingDetailUsesPointerNotHighestVersionAndExposesExactHistory() {
        ReviewFixture fixture = submittedFixture("Candidate V1");
        insertVersion(fixture.resultId(), 2, "Unrelated higher V2");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        var detail = reviewQueryService.pendingDetail(fixture.resultId());

        assertThat(detail.candidateVersionId()).isEqualTo(fixture.versionId());
        assertThat(detail.candidateVersionNumber()).isOne();
        assertThat(detail.candidateTitle()).isEqualTo("Candidate V1");
        assertThat(detail.history()).extracting(entry -> entry.action()).containsExactly("SUBMITTED");
    }

    @Test
    void pendingSupportReadIncludesActivityContextAndKeepsDraftAndCancelledMembership() {
        ReviewFixture cancelled = submittedFixture("Cancelled support");
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?",
                cancelled.activityId());
        ReviewFixture draft = submittedFixture("Draft support");
        jdbc.update("UPDATE activities SET execution_status = 'DRAFT' WHERE id = ?",
                draft.activityId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        var page = reviewQueryService.listPending(0, 20);

        var cancelledSummary = page.items().stream()
                .filter(item -> item.resultId().equals(cancelled.resultId()))
                .findFirst().orElseThrow();
        assertThat(cancelledSummary.schoolName()).startsWith(prefix + "-a");
        assertThat(cancelledSummary.activityTitle()).isEqualTo(
                jdbc.queryForObject("SELECT title FROM activities WHERE id = ?", String.class,
                        cancelled.activityId()));
        assertThat(cancelledSummary.activityExecutionStatus()).isEqualTo("CANCELLED");

        var draftDetail = reviewQueryService.pendingDetail(draft.resultId());
        assertThat(draftDetail.schoolName()).startsWith(prefix + "-a");
        assertThat(draftDetail.activityTitle()).isEqualTo(
                jdbc.queryForObject("SELECT title FROM activities WHERE id = ?", String.class,
                        draft.activityId()));
        assertThat(draftDetail.activityExecutionStatus()).isEqualTo("DRAFT");
        assertThat(draftDetail.candidateVersionId()).isEqualTo(draft.versionId());
        assertThat(draftDetail.history()).extracting(entry -> entry.action())
                .containsExactly("SUBMITTED");
    }

    @Test
    void governanceReadUsesExactPublicPointerAndDoesNotCreateRows() {
        ReviewFixture fixture = publishedFixture("Public pointer V1");
        UUID v2 = insertVersion(fixture.resultId(), 2, "Newer candidate V2");
        jdbc.update("UPDATE result_versions SET published_publicly_at = now() WHERE id = ?",
                fixture.versionId());
        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = 'PUBLIC',
                    current_candidate_version_id = ?,
                    current_public_version_id = ?,
                    public_visibility_blocked = false
                WHERE id = ?
                """, v2, fixture.versionId(), fixture.resultId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        int resultCountBefore = jdbc.queryForObject(
                "SELECT count(*) FROM activity_results WHERE school_id = ?", Integer.class, schoolA);
        int versionCountBefore = jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE result_id = ?", Integer.class, fixture.resultId());
        int historyCountBefore = historyCount(fixture.resultId());

        var page = governanceQueryService.list(0, 20, " public ", false, "  SLICE-C  ");
        var summary = page.items().stream()
                .filter(item -> item.resultId().equals(fixture.resultId()))
                .findFirst().orElseThrow();
        var detail = governanceQueryService.detail(fixture.resultId());

        assertThat(summary.currentPublicVersionId()).isEqualTo(fixture.versionId());
        assertThat(summary.publicStatus()).isEqualTo("PUBLIC");
        assertThat(detail.currentCandidateVersionId()).isEqualTo(v2);
        assertThat(detail.currentPublicVersionId()).isEqualTo(fixture.versionId());
        assertThat(detail.currentPublicVersion()).isNotNull();
        assertThat(detail.currentPublicVersion().versionId()).isEqualTo(fixture.versionId());
        assertThat(detail.currentPublicVersion().title()).isEqualTo("Public pointer V1");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM activity_results WHERE school_id = ?", Integer.class, schoolA))
                .isEqualTo(resultCountBefore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE result_id = ?", Integer.class, fixture.resultId()))
                .isEqualTo(versionCountBefore);
        assertThat(historyCount(fixture.resultId())).isEqualTo(historyCountBefore);
    }

    @Test
    void approveAppendsCandidateBoundDecisionWithoutPublishing() {
        ReviewFixture fixture = submittedFixture("V1");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        var result = reviewService.approve(fixture.resultId());

        assertThat(result.publicStatus()).isEqualTo("PLATFORM_APPROVED");
        assertThat(result.publicStatus()).isNotEqualTo("PUBLIC");
        assertThat(historyActions(fixture.resultId(), fixture.versionId()))
                .containsExactly("SUBMITTED", "APPROVED");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT published_publicly_at FROM result_versions WHERE id = ?",
                Object.class, fixture.versionId())).isNull();
        assertThatThrownBy(() -> reviewService.approve(fixture.resultId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(historyActions(fixture.resultId(), fixture.versionId()))
                .containsExactly("SUBMITTED", "APPROVED");
    }

    @Test
    void decisionFailsWhenSubmissionDoesNotBindCurrentCandidate() {
        ReviewFixture fixture = submittedFixture("V1");
        UUID v2 = insertVersion(fixture.resultId(), 2, "V2");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = ? WHERE id = ?",
                v2, fixture.resultId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        assertThatThrownBy(() -> reviewService.approve(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no open public review submission");
        assertThat(historyCount(fixture.resultId())).isOne();
        assertThat(resultStatus(fixture.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
    }

    @Test
    void rejectRequiresAndTrimsReasonWhilePreservingCandidate() {
        ReviewFixture fixture = submittedFixture("V1");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        for (String reason : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> reviewService.reject(fixture.resultId(), reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> reviewService.reject(fixture.resultId(), "x".repeat(2_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max 2000");
        assertThat(resultStatus(fixture.resultId())).isEqualTo("PENDING_PUBLIC_REVIEW");
        assertThat(historyCount(fixture.resultId())).isOne();

        var result = reviewService.reject(fixture.resultId(), "  needs evidence  ");

        assertThat(result.publicStatus()).isEqualTo("PLATFORM_REJECTED");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id"))
                .isEqualTo(fixture.versionId());
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM result_review_records
                WHERE result_id = ? AND action = 'REJECTED'
                """, String.class, fixture.resultId())).isEqualTo("needs evidence");
    }

    @Test
    void rejectedReplacementPreservesOldPublicPointerAndVisibility() {
        ReviewFixture original = publishedFixture("Public V1");
        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = 'PUBLIC', current_public_version_id = ?,
                    public_visibility_blocked = false
                WHERE id = ?
                """, original.versionId(), original.resultId());
        var edited = editorService.saveEditorContent(
                activityId(original.resultId()), command("Candidate V2"));
        editorService.publishInternal(original.resultId());
        reviewService.submit(original.resultId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);

        reviewService.reject(original.resultId(), "not yet");

        assertThat(resultColumn(original.resultId(), "current_candidate_version_id"))
                .isEqualTo(edited.currentCandidateVersionId());
        assertThat(resultColumn(original.resultId(), "current_public_version_id"))
                .isEqualTo(original.versionId());
        assertThat(resultColumn(original.resultId(), "public_visibility_blocked")).isEqualTo(false);
    }

    @Test
    void correctedCandidateStartsFreshReviewHistoryAndCanBeReviewedAgain() {
        ReviewFixture rejected = submittedFixture("V1");
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        reviewService.reject(rejected.resultId(), "fix it");
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        var corrected = editorService.saveEditorContent(
                activityId(rejected.resultId()), command("V2 corrected"));

        assertThat(historyActions(rejected.resultId(), rejected.versionId()))
                .containsExactly("SUBMITTED", "REJECTED");
        assertThat(historyActions(rejected.resultId(), corrected.currentCandidateVersionId())).isEmpty();

        editorService.publishInternal(rejected.resultId());
        reviewService.submit(rejected.resultId());
        authenticate(superAdminA, "SUPER_ADMIN", null, null);
        reviewService.approve(rejected.resultId());

        assertThat(historyActions(rejected.resultId(), rejected.versionId()))
                .containsExactly("SUBMITTED", "REJECTED");
        assertThat(historyActions(rejected.resultId(), corrected.currentCandidateVersionId()))
                .containsExactly("SUBMITTED", "APPROVED");
    }

    @Test
    void reviewRepositorySurfaceIsAppendOnly() {
        assertThat(List.of(ResultReviewRecordRepository.class.getDeclaredMethods()))
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder("append", "findByResult", "findByResultAndVersion")
                .doesNotContain("save", "update", "delete");
    }

    @Test
    void reviewHistoryCompositeForeignKeyRejectsVersionFromAnotherResult() {
        ReviewFixture left = publishedFixture("Left");
        ReviewFixture right = publishedFixture("Right");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action,
                    submitted_by, submitted_at, created_at
                ) VALUES (?, ?, ?, 'SUBMITTED', ?, now(), now())
                """, UUID.randomUUID(), left.resultId(), right.versionId(), adminA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(historyCount(left.resultId())).isZero();
        assertThat(historyCount(right.resultId())).isZero();
    }

    @Test
    void concurrentDecisionsCommitExactlyOneStateAndMatchingHistory() throws Exception {
        ReviewFixture fixture = submittedFixture("V1");
        CyclicBarrier loaded = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            loaded.await(10, TimeUnit.SECONDS);
            return value;
        }).when(activityResults).findById(any(ActivityResultId.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Throwable approveFailure;
        Throwable rejectFailure;
        try {
            Future<Throwable> approve = executor.submit(() -> decide(
                    superAdminA, true, fixture.resultId(), ready, start));
            Future<Throwable> reject = executor.submit(() -> decide(
                    superAdminB, false, fixture.resultId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            approveFailure = approve.get(20, TimeUnit.SECONDS);
            rejectFailure = reject.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        int successes = (approveFailure == null ? 1 : 0) + (rejectFailure == null ? 1 : 0);
        Throwable conflict = approveFailure == null ? rejectFailure : approveFailure;
        assertThat(successes).isOne();
        assertThat(conflict).isNotNull();
        assertThat(isConcurrencyConflict(conflict)).isTrue();
        String status = resultStatus(fixture.resultId());
        List<String> actions = historyActions(fixture.resultId(), fixture.versionId());
        assertThat(actions).hasSize(2).startsWith("SUBMITTED");
        assertThat(actions.get(1)).isEqualTo(
                "PLATFORM_APPROVED".equals(status) ? "APPROVED" : "REJECTED");
    }

    private ReviewFixture publishedFixture(String title) {
        UUID activityId = insertActivity(schoolA);
        var saved = editorService.saveEditorContent(activityId, command(title));
        editorService.publishInternal(saved.resultId());
        return new ReviewFixture(activityId, saved.resultId(), saved.currentCandidateVersionId());
    }

    private ReviewFixture submittedFixture(String title) {
        ReviewFixture fixture = publishedFixture(title);
        reviewService.submit(fixture.resultId());
        return fixture;
    }

    private SaveActivityResultContentCommand command(String title) {
        return new SaveActivityResultContentCommand(title, "Summary " + title, List.of("score"), List.of());
    }

    private Throwable decide(
            UUID reviewerId,
            boolean approve,
            UUID resultId,
            CountDownLatch ready,
            CountDownLatch start) {
        authenticate(reviewerId, "SUPER_ADMIN", null, null);
        try {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            if (approve) reviewService.approve(resultId);
            else reviewService.reject(resultId, "concurrent reject");
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID insertActivity(UUID schoolId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, 'PUBLISHED', 'NOT_SUBMITTED', ?)
                """, id, schoolId, prefix + "-activity-" + id, adminA);
        return id;
    }

    private UUID insertVersion(UUID resultId, int number, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights, media_refs
                ) VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb)
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
                """, id, prefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), platformRole);
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

    private UUID activityId(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT activity_id FROM activity_results WHERE id = ?", UUID.class, resultId);
    }

    private String resultStatus(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT result_public_status FROM activity_results WHERE id = ?", String.class, resultId);
    }

    private Object resultColumn(UUID resultId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM activity_results WHERE id = ?", Object.class, resultId);
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
                    && current.getMessage().contains("no open public review submission")) {
                return true;
            }
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private String resultPublicStatus(UUID resultId) {
        return jdbc.queryForObject("SELECT result_public_status FROM activity_results WHERE id = ?",
                String.class, resultId);
    }

    private record ReviewFixture(UUID activityId, UUID resultId, UUID versionId) {
    }
}
