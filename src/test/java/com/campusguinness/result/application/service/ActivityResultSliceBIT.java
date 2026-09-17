package com.campusguinness.result.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

class ActivityResultSliceBIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private ActivityResultApplicationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private ResultVersionRepository resultVersions;

    private final String runPrefix = "slice-b-" + UUID.randomUUID();
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a");
        adminB = insertUser("admin-b");
        studentA = insertUser("student-a");
        insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        insertMembership(studentA, schoolA, "STUDENT");
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL "
                + "WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("DELETE FROM activity_results WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM media WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?, ?, ?)", adminA, adminB, studentA);
        jdbc.update("DELETE FROM schools WHERE id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", adminA, adminB, studentA);
    }

    @Test
    void readEditorForActivityWithoutResultDoesNotCreateRows() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        long beforeResults = count("activity_results");
        long beforeVersions = count("result_versions");

        var editor = service.readEditor(activityId);

        assertThat(editor.resultId()).isNull();
        assertThat(editor.candidateContent()).isNull();
        assertThat(count("activity_results")).isEqualTo(beforeResults);
        assertThat(count("result_versions")).isEqualTo(beforeVersions);
    }

    @Test
    void firstSaveLazilyCreatesResultV1AndCandidatePointer() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");

        var editor = save(activityId, "Title", "Summary");

        assertThat(editor.resultId()).isNotNull();
        assertThat(editor.currentCandidateVersionId()).isNotNull();
        assertThat(editor.currentInternalVersionId()).isNull();
        assertThat(editor.currentPublicVersionId()).isNull();
        assertThat(editor.publicVisibilityBlocked()).isFalse();
        assertThat(countResultRows(activityId)).isOne();
        assertThat(countVersions(editor.resultId())).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT version_number FROM result_versions WHERE id = ?",
                Integer.class, editor.currentCandidateVersionId())).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "IN_PROGRESS", "ENDED"})
    void allowedActivityStatesCanSave(String status) {
        UUID activityId = insertActivity(schoolA, status);

        assertThat(save(activityId, status + " title", "Summary").resultId()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CANCELLED"})
    void deniedActivityStatesCannotSave(String status) {
        UUID activityId = insertActivity(schoolA, status);

        assertThatThrownBy(() -> save(activityId, "Title", "Summary"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(status);
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void cancelledExistingResultRejectsContentEditAndPreservesRows() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?", activityId);

        assertThatThrownBy(() -> save(activityId, "V2", "Summary V2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
        assertThat(countResultRows(activityId)).isOne();
        assertThat(countVersions(first.resultId())).isOne();
        assertThat(resultColumn(first.resultId(), "current_candidate_version_id"))
                .isEqualTo(first.currentCandidateVersionId());
    }

    @ParameterizedTest
    @CsvSource({
            " ,Summary,title required",
            "'',Summary,title required",
            "Title, ,summaryText required"
    })
    void requiredContentValidation(String title, String summary, String message) {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");

        assertThatThrownBy(() -> save(activityId, title, summary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void contentValidationRejectsMaxesBlankHighlightsAndMediaRefs() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("t".repeat(201), "Summary", List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title max 200");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "s".repeat(10_001), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("summaryText max 10000");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "Summary",
                        java.util.Collections.nCopies(21, "x"), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scoreHighlights max 20");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "Summary", List.of(" "), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scoreHighlights required");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "Summary", List.of("x".repeat(201)), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scoreHighlights max 200");
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "Summary", List.of(),
                        java.util.Collections.nCopies(21, UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mediaRefs max 20");
        UUID duplicate = UUID.randomUUID();
        assertThatThrownBy(() -> service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand("Title", "Summary", List.of(), List.of(duplicate, duplicate))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate mediaRef");
    }

    @Test
    void nullMediaRefsAreNormalizedAndPersistedAsEmpty() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");

        var saved = saveWithMedia(activityId, "V1", null);

        assertThat(saved.candidateContent().mediaRefs()).isEmpty();
        assertThat(readMediaRefs(saved.currentCandidateVersionId())).isEmpty();
    }

    @Test
    void emptyMediaRefsAreAccepted() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");

        var saved = saveWithMedia(activityId, "V1", List.of());

        assertThat(saved.candidateContent().mediaRefs()).isEmpty();
        assertThat(countVersions(saved.resultId())).isOne();
    }

    @Test
    void eligibleMediaRefIsAcceptedOnFirstSaveAndRoundTrips() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID mediaId = insertMedia(schoolA, activityId, adminA);

        var saved = saveWithMedia(activityId, "V1", List.of(mediaId));

        assertThat(saved.candidateContent().mediaRefs()).containsExactly(mediaId);
        assertThat(readMediaRefs(saved.currentCandidateVersionId())).containsExactly(mediaId);
        assertThat(service.readEditor(activityId).candidateContent().mediaRefs()).containsExactly(mediaId);
    }

    @Test
    void multipleEligibleMediaRefsRoundTripInOrder() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        List<UUID> mediaIds = List.of(
                insertMedia(schoolA, activityId, adminA),
                insertMedia(schoolA, activityId, adminA),
                insertMedia(schoolA, activityId, adminA));

        var saved = saveWithMedia(activityId, "V1", mediaIds);

        assertThat(readMediaRefs(saved.currentCandidateVersionId())).containsExactlyElementsOf(mediaIds);
    }

    @Test
    void coreEditWithEligibleMediaCreatesNewVersionAndPreservesOldSnapshot() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID firstMedia = insertMedia(schoolA, activityId, adminA);
        UUID secondMedia = insertMedia(schoolA, activityId, adminA);
        var first = saveWithMedia(activityId, "V1", List.of(firstMedia));

        var edited = saveWithMedia(activityId, "V2", List.of(firstMedia, secondMedia));

        assertThat(countVersions(first.resultId())).isEqualTo(2);
        assertThat(edited.currentCandidateVersionId()).isNotEqualTo(first.currentCandidateVersionId());
        assertThat(readMediaRefs(first.currentCandidateVersionId())).containsExactly(firstMedia);
        assertThat(readMediaRefs(edited.currentCandidateVersionId())).containsExactly(firstMedia, secondMedia);
    }

    @Test
    void missingMediaRefIsRejectedWithoutPersistence() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid or ineligible media reference");
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void crossSchoolMediaRefIsRejectedWithoutLeakingOwnership() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID otherActivityId = insertActivity(schoolB, "PUBLISHED");
        UUID mediaId = insertMedia(schoolB, otherActivityId, adminB);

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", List.of(mediaId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid or ineligible media reference");
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void crossActivityMediaRefIsRejected() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID otherActivityId = insertActivity(schoolA, "PUBLISHED");
        UUID mediaId = insertMedia(schoolA, otherActivityId, adminA);

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", List.of(mediaId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid or ineligible media reference");
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void duplicateEligibleMediaRefIsRejectedWithoutSilentDeduplication() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID mediaId = insertMedia(schoolA, activityId, adminA);

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", List.of(mediaId, mediaId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate mediaRef");
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void twentyOneMediaRefsAreRejectedBeforeLookup() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        List<UUID> mediaIds = java.util.stream.Stream.generate(UUID::randomUUID).limit(21).toList();

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", mediaIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mediaRefs max 20");
        assertThat(countResultRows(activityId)).isZero();
    }

    @Test
    void twentyEligibleMediaRefsAreAccepted() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        List<UUID> mediaIds = java.util.stream.IntStream.range(0, 20)
                .mapToObj(ignored -> insertMedia(schoolA, activityId, adminA))
                .toList();

        var saved = saveWithMedia(activityId, "V1", mediaIds);

        assertThat(readMediaRefs(saved.currentCandidateVersionId())).containsExactlyElementsOf(mediaIds);
    }

    @Test
    void firstSaveMediaValidationFailureLeavesNoPartialRowsOrPointers() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        long resultsBefore = count("activity_results");
        long versionsBefore = count("result_versions");

        assertThatThrownBy(() -> saveWithMedia(activityId, "V1", List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(count("activity_results")).isEqualTo(resultsBefore);
        assertThat(count("result_versions")).isEqualTo(versionsBefore);
    }

    @Test
    void coreEditMediaValidationFailurePreservesAggregateAndVersionHistory() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        UUID mediaId = insertMedia(schoolA, activityId, adminA);
        var first = saveWithMedia(activityId, "V1", List.of(mediaId));
        int persistenceVersion = resultIntColumn(first.resultId(), "version");
        String internalStatus = resultStringColumn(first.resultId(), "result_internal_status");
        String publicStatus = resultStringColumn(first.resultId(), "result_public_status");

        assertThatThrownBy(() -> saveWithMedia(activityId, "V2", List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(countVersions(first.resultId())).isOne();
        assertThat(resultColumn(first.resultId(), "current_candidate_version_id"))
                .isEqualTo(first.currentCandidateVersionId());
        assertThat(resultColumn(first.resultId(), "current_internal_version_id")).isNull();
        assertThat(resultColumn(first.resultId(), "current_public_version_id")).isNull();
        assertThat(resultStringColumn(first.resultId(), "result_internal_status")).isEqualTo(internalStatus);
        assertThat(resultStringColumn(first.resultId(), "result_public_status")).isEqualTo(publicStatus);
        assertThat(resultIntColumn(first.resultId(), "version")).isEqualTo(persistenceVersion);
        assertThat(title(first.currentCandidateVersionId())).isEqualTo("V1");
    }

    @Test
    void subsequentCoreSaveCreatesImmutableV2FromExplicitCandidatePointer() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        UUID v1 = first.currentCandidateVersionId();
        insertHistoricalVersion(first.resultId(), 99, "Historical latest");

        var second = save(activityId, "V2", "Summary V2");

        assertThat(second.currentCandidateVersionId()).isNotEqualTo(v1);
        assertThat(countVersions(first.resultId())).isEqualTo(3);
        assertThat(title(v1)).isEqualTo("V1");
        assertThat(second.candidateContent().versionNumber()).isEqualTo(100);
        assertThat(second.candidateContent().title()).isEqualTo("V2");
    }

    @Test
    void internalPublishedCoreEditPreservesOldInternalPointerUntilRepublish() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        service.publishInternal(first.resultId());
        UUID v1 = first.currentCandidateVersionId();

        var edited = save(activityId, "V2", "Summary V2");

        assertThat(edited.internalStatus()).isEqualTo("DRAFT");
        assertThat(edited.currentInternalVersionId()).isEqualTo(v1);
        assertThat(edited.currentCandidateVersionId()).isNotEqualTo(v1);

        service.publishInternal(edited.resultId());
        UUID v2 = edited.currentCandidateVersionId();
        assertThat(internalStamp(v2)).isNotNull();
        assertThat(internalStamp(v1)).isNotNull();
    }

    @Test
    void publicReplacementEditPreservesOldPublicPointerAndVisibility() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        UUID v1 = first.currentCandidateVersionId();
        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = 'PUBLIC', current_public_version_id = ?, public_visibility_blocked = false
                WHERE id = ?
                """, v1, first.resultId());

        var edited = save(activityId, "V2", "Summary V2");

        assertThat(edited.publicStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(edited.currentPublicVersionId()).isEqualTo(v1);
        assertThat(edited.publicVisibilityBlocked()).isFalse();
        assertThat(edited.currentCandidateVersionId()).isNotEqualTo(v1);
    }

    @Test
    void rejectedCandidateCorrectionCreatesV3AndResetsToNotSubmitted() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var v1 = save(activityId, "V1", "Summary V1");
        var v2 = save(activityId, "V2", "Summary V2");
        jdbc.update("UPDATE activity_results SET result_public_status = 'PLATFORM_REJECTED' WHERE id = ?",
                v2.resultId());

        var v3 = save(activityId, "V3", "Summary V3");

        assertThat(v3.publicStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(v3.currentCandidateVersionId()).isNotEqualTo(v2.currentCandidateVersionId());
        assertThat(title(v2.currentCandidateVersionId())).isEqualTo("V2");
        assertThat(countVersions(v1.resultId())).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED"})
    void reviewBoundStatesDenyCoreEdit(String publicStatus) {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        jdbc.update("UPDATE activity_results SET result_public_status = ? WHERE id = ?", publicStatus, first.resultId());

        assertThatThrownBy(() -> save(activityId, "V2", "Summary V2"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot");
        assertThat(countVersions(first.resultId())).isOne();
    }

    @Test
    void withdrawInternalWithoutPublicPointerDoesNotFabricateTakedown() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        service.publishInternal(first.resultId());

        var withdrawn = service.withdrawInternal(first.resultId());

        assertThat(withdrawn.internalStatus()).isEqualTo("INTERNAL_WITHDRAWN");
        assertThat(withdrawn.publicStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(resultColumn(first.resultId(), "current_candidate_version_id"))
                .isEqualTo(first.currentCandidateVersionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED", "PLATFORM_REJECTED", "PUBLIC", "ANOMALY_PENDING"})
    void withdrawInternalWithActivePublicPointerBlocksVisibilityForAllPublicWorkflowStates(String publicStatus) {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        service.publishInternal(first.resultId());
        UUID v1 = first.currentCandidateVersionId();
        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = ?, current_public_version_id = ?, public_visibility_blocked = false
                WHERE id = ?
                """, publicStatus, v1, first.resultId());

        service.withdrawInternal(first.resultId());

        assertThat(resultColumn(first.resultId(), "result_internal_status")).isEqualTo("INTERNAL_WITHDRAWN");
        assertThat(resultColumn(first.resultId(), "result_public_status")).isEqualTo("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(first.resultId(), "current_public_version_id")).isEqualTo(v1);
        assertThat((Boolean) resultColumn(first.resultId(), "public_visibility_blocked")).isTrue();
        assertThat(resultColumn(first.resultId(), "current_candidate_version_id")).isNull();
    }

    @Test
    void returnToDraftDoesNotClearBlockOrReExposeOldPublicPointer() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        service.publishInternal(first.resultId());
        UUID v1 = first.currentCandidateVersionId();
        jdbc.update("""
                UPDATE activity_results
                SET result_public_status = 'PUBLIC', current_public_version_id = ?, public_visibility_blocked = false
                WHERE id = ?
                """, v1, first.resultId());
        service.withdrawInternal(first.resultId());

        service.returnToDraft(first.resultId());

        assertThat(resultColumn(first.resultId(), "result_internal_status")).isEqualTo("DRAFT");
        assertThat(resultColumn(first.resultId(), "result_public_status")).isEqualTo("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(first.resultId(), "current_public_version_id")).isEqualTo(v1);
        assertThat((Boolean) resultColumn(first.resultId(), "public_visibility_blocked")).isTrue();
    }

    @Test
    void sameSchoolAllowedOtherSchoolAndStudentDenied() {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        assertThat(save(activityId, "V1", "Summary").resultId()).isNotNull();

        authenticate(adminB, "SCHOOL_ADMIN", schoolB, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> save(activityId, "V2", "Summary"))
                .isInstanceOf(IdentityApplicationException.class);

        authenticate(studentA, "STUDENT", schoolA, "STUDENT");
        assertThatThrownBy(() -> save(activityId, "V2", "Summary"))
                .isInstanceOf(IdentityApplicationException.class);
    }

    @Test
    void concurrentSavesCommitExactlyOneV2AndRejectTheCompetingSave() throws Exception {
        UUID activityId = insertActivity(schoolA, "PUBLISHED");
        var first = save(activityId, "V1", "Summary V1");
        int initialPersistenceVersion = resultIntColumn(first.resultId(), "version");
        CyclicBarrier versionAllocationBarrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            int nextVersion = (int) invocation.callRealMethod();
            versionAllocationBarrier.await(10, TimeUnit.SECONDS);
            return nextVersion;
        }).when(resultVersions).nextVersionNumberFor(any(ActivityResultId.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Throwable leftFailure;
        Throwable rightFailure;
        try {
            Future<Throwable> left = executor.submit(() -> concurrentSave(
                    activityId, "V2-left", ready, start));
            Future<Throwable> right = executor.submit(() -> concurrentSave(
                    activityId, "V2-right", ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            leftFailure = left.get(20, TimeUnit.SECONDS);
            rightFailure = right.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        int versionCount = jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE result_id = ?", Integer.class, first.resultId());
        int distinctVersionCount = jdbc.queryForObject(
                "SELECT count(DISTINCT version_number) FROM result_versions WHERE result_id = ?",
                Integer.class, first.resultId());
        UUID candidateId = jdbc.queryForObject(
                "SELECT current_candidate_version_id FROM activity_results WHERE id = ?",
                UUID.class, first.resultId());

        int successCount = (leftFailure == null ? 1 : 0) + (rightFailure == null ? 1 : 0);
        Throwable conflict = leftFailure == null ? rightFailure : leftFailure;
        assertThat(successCount).isOne();
        assertThat(conflict).isNotNull();
        assertThat(isRecognizedConcurrencyConflict(conflict)).isTrue();
        assertThat(countResultRows(activityId)).isOne();
        assertThat(versionCount).isEqualTo(2);
        assertThat(distinctVersionCount).isEqualTo(2);
        assertThat(jdbc.queryForList(
                "SELECT version_number FROM result_versions WHERE result_id = ? ORDER BY version_number",
                Integer.class, first.resultId())).containsExactly(1, 2);
        assertThat(candidateId).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE id = ? AND result_id = ?",
                Integer.class, candidateId, first.resultId())).isOne();
        assertThat(title(candidateId)).isIn("V2-left", "V2-right");
        assertThat(title(first.currentCandidateVersionId())).isEqualTo("V1");
        assertThat(resultIntColumn(first.resultId(), "version")).isGreaterThan(initialPersistenceVersion);
    }

    private com.campusguinness.result.application.result.ActivityResultEditorResult save(
            UUID activityId, String title, String summary) {
        return service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand(title, summary, List.of(), List.of()));
    }

    private com.campusguinness.result.application.result.ActivityResultEditorResult saveWithMedia(
            UUID activityId, String title, List<UUID> mediaRefs) {
        return service.saveEditorContent(activityId,
                new SaveActivityResultContentCommand(title, "Summary " + title, List.of(), mediaRefs));
    }

    private Throwable concurrentSave(
            UUID activityId,
            String title,
            CountDownLatch ready,
            CountDownLatch start) {
        authenticate(adminA, "SCHOOL_ADMIN", schoolA, "SCHOOL_ADMIN");
        try {
            ready.countDown();
            start.await(10, java.util.concurrent.TimeUnit.SECONDS);
            save(activityId, title, "Summary " + title);
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
                """, id, schoolId, runPrefix + "-activity-" + id, executionStatus, adminA);
        return id;
    }

    private UUID insertMedia(UUID schoolId, UUID activityId, UUID uploaderId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO media(
                    id, school_id, activity_id, uploader_id, file_key, file_name,
                    file_type, file_format, file_size_bytes
                ) VALUES (?, ?, ?, ?, ?, ?, 'IMAGE', 'PNG', 1024)
                """, id, schoolId, activityId, uploaderId,
                runPrefix + "/" + id, id + ".png");
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
                """, id, runPrefix + "-" + label, "USCC", runPrefix.substring(0, 8) + suffix + "u",
                runPrefix.substring(0, 8) + suffix + "i", "PRIMARY", "Beijing",
                "Address", "Contact", "13800000000", runPrefix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                id, runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8),
                "{noop}password", "NORMAL");
        return id;
    }

    private void insertMembership(UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private void authenticate(UUID userId, String platformRole, UUID schoolId, String membershipRole) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + platformRole)),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, membershipRole)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    private void insertHistoricalVersion(UUID resultId, int versionNumber, String title) {
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights, media_refs
                ) VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb)
                """, UUID.randomUUID(), resultId, versionNumber, title, title + " summary");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long countResultRows(UUID activityId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM activity_results WHERE activity_id = ?", Long.class, activityId);
    }

    private long countVersions(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE result_id = ?", Long.class, resultId);
    }

    private String title(UUID versionId) {
        return jdbc.queryForObject("SELECT title FROM result_versions WHERE id = ?", String.class, versionId);
    }

    private java.time.Instant internalStamp(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT published_internally_at FROM result_versions WHERE id = ?",
                java.time.Instant.class, versionId);
    }

    private Object resultColumn(UUID resultId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM activity_results WHERE id = ?", Object.class, resultId);
    }

    private int resultIntColumn(UUID resultId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM activity_results WHERE id = ?", Integer.class, resultId);
    }

    private String resultStringColumn(UUID resultId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM activity_results WHERE id = ?", String.class, resultId);
    }

    private List<UUID> readMediaRefs(UUID versionId) {
        String json = jdbc.queryForObject(
                "SELECT media_refs::text FROM result_versions WHERE id = ?", String.class, versionId);
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new AssertionError("Stored media_refs must be valid UUID JSON", e);
        }
    }

    private boolean isRecognizedConcurrencyConflict(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
