package com.campusguinness.result.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewAction;
import com.campusguinness.result.internal.domain.ResultVersionId;
import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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

import javax.sql.DataSource;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

class ActivityResultSliceD2BIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ActivityResultGovernanceApplicationService governanceService;
    @Autowired private ActivityResultApplicationService resultService;
    @Autowired private ActivityResultPublicationApplicationService publicationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @MockitoSpyBean private ActivityResultRepository activityResults;
    @MockitoSpyBean private ResultVersionRepository resultVersions;
    @MockitoSpyBean private ResultReviewRecordRepository reviewRecords;

    private final String prefix = "slice-d2b-" + UUID.randomUUID();
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
    void resetPreservesPointersBlockVersionAndTakedownHistoryAndAppendsExactReset() {
        Fixture fixture = takedownFixture("PUBLISHED");
        Map<String, Object> versionBefore = versionSnapshot(fixture.publicVersionId());
        Instant before = Instant.now();

        var response = governanceService.resetTakedown(fixture.resultId(), "  governance complete  ");

        assertThat(response.publicStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_internal_version_id"))
                .isEqualTo(fixture.internalVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(versionSnapshot(fixture.publicVersionId())).isEqualTo(versionBefore);
        assertThat(historyCount(fixture.resultId(), "TAKEDOWN")).isOne();
        assertThat(historyCount(fixture.resultId(), "RESET")).isOne();

        Map<String, Object> resetRow = jdbc.queryForMap("""
                SELECT result_id, result_version_id, reviewer_id, reviewed_at, created_at, reason,
                       submitted_by, submitted_at
                FROM result_review_records WHERE result_id = ? AND action = 'RESET'
                """, fixture.resultId());
        assertThat(resetRow.get("result_id")).isEqualTo(fixture.resultId());
        assertThat(resetRow.get("result_version_id")).isEqualTo(fixture.publicVersionId());
        assertThat(resetRow.get("reviewer_id")).isEqualTo(superAdmin);
        assertThat(((Timestamp) resetRow.get("reviewed_at")).toInstant()).isAfterOrEqualTo(before);
        assertThat(resetRow.get("created_at")).isEqualTo(resetRow.get("reviewed_at"));
        assertThat(resetRow.get("reason")).isEqualTo("governance complete");
        assertThat(resetRow.get("submitted_by")).isNull();
        assertThat(resetRow.get("submitted_at")).isNull();
    }

    @Test
    void wrongStatesAndBrokenPointerInvariantsFailClosedWithoutResetHistory() {
        for (String status : List.of("PUBLIC", "NOT_SUBMITTED", "PENDING_PUBLIC_REVIEW",
                "PLATFORM_APPROVED", "PLATFORM_REJECTED", "ANOMALY_PENDING")) {
            Fixture fixture = fixture(status, "PUBLISHED", true);
            assertThatThrownBy(() -> governanceService.resetTakedown(fixture.resultId(), "reason"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PLATFORM_TAKEDOWN");
            assertThat(historyCount(fixture.resultId(), "RESET")).isZero();
        }

        Fixture missingPointer = takedownFixture("PUBLISHED");
        jdbc.update("UPDATE activity_results SET current_public_version_id = NULL WHERE id = ?",
                missingPointer.resultId());
        assertThatThrownBy(() -> governanceService.resetTakedown(missingPointer.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("currentPublicVersionId");

        Fixture unblocked = takedownFixture("PUBLISHED");
        jdbc.update("UPDATE activity_results SET public_visibility_blocked = false WHERE id = ?",
                unblocked.resultId());
        assertThatThrownBy(() -> governanceService.resetTakedown(unblocked.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("blocked public visibility");

        Fixture candidate = takedownFixture("PUBLISHED");
        UUID candidateId = insertVersion(candidate.resultId(), 2, "Unexpected candidate", null);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = ? WHERE id = ?",
                candidateId, candidate.resultId());
        assertThatThrownBy(() -> governanceService.resetTakedown(candidate.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no current candidate");

        Fixture missingVersion = takedownFixture("PUBLISHED");
        doReturn(Optional.empty()).when(resultVersions)
                .findById(new ResultVersionId(missingVersion.publicVersionId()));
        assertThatThrownBy(() -> governanceService.resetTakedown(missingVersion.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not found");

        reset(resultVersions);
        Fixture target = takedownFixture("PUBLISHED");
        Fixture foreign = takedownFixture("PUBLISHED");
        var foreignVersion = resultVersions.findById(
                new ResultVersionId(foreign.publicVersionId())).orElseThrow();
        doReturn(Optional.of(foreignVersion)).when(resultVersions)
                .findById(new ResultVersionId(target.publicVersionId()));
        assertThatThrownBy(() -> governanceService.resetTakedown(target.resultId(), "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not belong");

        assertThat(historyCount(missingPointer.resultId(), "RESET")).isZero();
        assertThat(historyCount(unblocked.resultId(), "RESET")).isZero();
        assertThat(historyCount(candidate.resultId(), "RESET")).isZero();
        assertThat(historyCount(missingVersion.resultId(), "RESET")).isZero();
        assertThat(historyCount(target.resultId(), "RESET")).isZero();
    }

    @Test
    void reasonNormalizationBoundariesAndAuthorizationAreEnforced() {
        Fixture invalid = takedownFixture("PUBLISHED");
        assertThatThrownBy(() -> governanceService.resetTakedown(invalid.resultId(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason required");
        assertThatThrownBy(() -> governanceService.resetTakedown(invalid.resultId(), "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason required");
        assertThatThrownBy(() -> governanceService.resetTakedown(
                invalid.resultId(), "  " + "x".repeat(2001) + "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max 2000");
        assertThat(resultStatus(invalid.resultId())).isEqualTo("PLATFORM_TAKEDOWN");

        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> governanceService.resetTakedown(invalid.resultId(), "reason"))
                .isInstanceOf(IdentityApplicationException.class);
        authenticate(student, "STUDENT", schoolId, "STUDENT");
        assertThatThrownBy(() -> governanceService.resetTakedown(invalid.resultId(), "reason"))
                .isInstanceOf(IdentityApplicationException.class);

        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        Fixture max = takedownFixture("PUBLISHED");
        governanceService.resetTakedown(max.resultId(), "  " + "x".repeat(2000) + "  ");
        assertThat(historyReason(max.resultId(), "RESET")).hasSize(2000);
        assertThat(historyReason(max.resultId(), "RESET")).isEqualTo("x".repeat(2000));
    }

    @Test
    void cancelledActivityAllowsResetButStillDeniesOrdinaryRecoveryMutations() {
        Fixture fixture = takedownFixture("CANCELLED");

        governanceService.resetTakedown(fixture.resultId(), "close governance workflow");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT execution_status FROM activities WHERE id = ?",
                String.class, fixture.activityId())).isEqualTo("CANCELLED");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);

        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> resultService.saveEditorContent(fixture.activityId(),
                new SaveActivityResultContentCommand("Recovery", "Summary", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThatThrownBy(() -> resultService.publishInternal(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> publicationService.makePublic(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
    }

    @Test
    void takedownBlocksCoreSaveUntilResetThenAllowsBlockedRecovery() {
        Fixture fixture = takedownFixture("PUBLISHED");
        int versionsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM result_versions WHERE result_id = ?", Integer.class, fixture.resultId());

        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> resultService.saveEditorContent(fixture.activityId(),
                new SaveActivityResultContentCommand("Blocked", "Must wait for reset", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM result_versions WHERE result_id = ?",
                Integer.class, fixture.resultId())).isEqualTo(versionsBefore);

        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        governanceService.resetTakedown(fixture.resultId(), "reset before replacement");
        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        var saved = resultService.saveEditorContent(fixture.activityId(),
                new SaveActivityResultContentCommand("Replacement", "Allowed after reset", List.of(), List.of()));

        assertThat(saved.currentCandidateVersionId()).isNotNull();
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
    }

    @Test
    void publishInternalRejectsCancelledActivityWithOtherwiseValidResultState() {
        Fixture fixture = fixture("NOT_SUBMITTED", "PUBLISHED", false);
        UUID candidate = insertVersion(fixture.resultId(), 2, "Candidate", null);
        jdbc.update("UPDATE activity_results SET result_internal_status = 'DRAFT', current_candidate_version_id = ?, "
                + "current_internal_version_id = NULL WHERE id = ?", candidate, fixture.resultId());
        jdbc.update("UPDATE activities SET execution_status = 'CANCELLED' WHERE id = ?", fixture.activityId());

        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> resultService.publishInternal(fixture.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isEqualTo(candidate);
        assertThat(resultColumn(fixture.resultId(), "current_internal_version_id")).isNull();
    }

    @Test
    void withdrawAndReturnToDraftRejectCancelledActivityBeforeDomainMutation() {
        Fixture withdrawn = fixture("NOT_SUBMITTED", "CANCELLED", false);
        jdbc.update("UPDATE activity_results SET result_internal_status = 'INTERNAL_PUBLISHED' WHERE id = ?",
                withdrawn.resultId());
        authenticate(schoolAdmin, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
        assertThatThrownBy(() -> resultService.withdrawInternal(withdrawn.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultColumn(withdrawn.resultId(), "result_internal_status"))
                .isEqualTo("INTERNAL_PUBLISHED");

        Fixture draft = fixture("NOT_SUBMITTED", "CANCELLED", false);
        jdbc.update("UPDATE activity_results SET result_internal_status = 'INTERNAL_WITHDRAWN' WHERE id = ?",
                draft.resultId());
        assertThatThrownBy(() -> resultService.returnToDraft(draft.resultId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CANCELLED");
        assertThat(resultColumn(draft.resultId(), "result_internal_status"))
                .isEqualTo("INTERNAL_WITHDRAWN");
    }

    @Test
    void resetHistoryFailureRollsBackAggregateAndPreservesTakedown() {
        Fixture fixture = takedownFixture("PUBLISHED");
        doThrow(new IllegalStateException("history unavailable")).when(reviewRecords)
                .append(argThat(record -> record.action() == ResultReviewAction.RESET));

        assertThatThrownBy(() -> governanceService.resetTakedown(fixture.resultId(), "rollback"))
                .isInstanceOf(IllegalStateException.class).hasMessage("history unavailable");

        assertThat(resultStatus(fixture.resultId())).isEqualTo("PLATFORM_TAKEDOWN");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "current_internal_version_id"))
                .isEqualTo(fixture.internalVersionId());
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(historyCount(fixture.resultId(), "TAKEDOWN")).isOne();
        assertThat(historyCount(fixture.resultId(), "RESET")).isZero();
    }

    @Test
    void concurrentDoubleResetHasExactlyOneSuccessAndOneResetHistory() throws Exception {
        Fixture fixture = takedownFixture("PUBLISHED");
        barrierOnAggregateLoad();

        RaceResult race = runRace(
                () -> resetInThread(fixture.resultId(), "first"),
                () -> resetInThread(fixture.resultId(), "second"));

        assertExactlyOneSuccess(race);
        assertThat(isConcurrencyOrStateConflict(race.failure())).isTrue();
        assertThat(resultStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(resultColumn(fixture.resultId(), "current_public_version_id"))
                .isEqualTo(fixture.publicVersionId());
        assertThat(resultColumn(fixture.resultId(), "current_candidate_version_id")).isNull();
        assertThat(resultColumn(fixture.resultId(), "public_visibility_blocked")).isEqualTo(true);
        assertThat(historyCount(fixture.resultId(), "TAKEDOWN")).isOne();
        assertThat(historyCount(fixture.resultId(), "RESET")).isOne();
    }

    @Test
    void resetWinsAgainstStaleDuplicateTakedownWithoutSecondTakedownHistory() throws Exception {
        Fixture fixture = takedownFixture("PUBLISHED");
        barrierOnAggregateLoad();

        RaceResult race = runRace(
                () -> resetInThread(fixture.resultId(), "reset"),
                () -> takedownInThread(fixture.resultId(), "duplicate"));

        assertExactlyOneSuccess(race);
        assertThat(race.firstFailure()).isNull();
        assertThat(isConcurrencyOrStateConflict(race.secondFailure())).isTrue();
        assertThat(resultStatus(fixture.resultId())).isEqualTo("NOT_SUBMITTED");
        assertThat(historyCount(fixture.resultId(), "RESET")).isOne();
        assertThat(historyCount(fixture.resultId(), "TAKEDOWN")).isOne();
    }

    @Test
    void v023EnforcesResetShapeSameResultBindingAndPreservesExistingActions() {
        Fixture fixture = takedownFixture("PUBLISHED");
        insertHistory(fixture, "RESET", null, null, superAdmin, Instant.now(), "valid reset");
        insertHistory(fixture, "SUBMITTED", schoolAdmin, Instant.now(), null, null, null);
        insertHistory(fixture, "APPROVED", null, null, superAdmin, Instant.now(), null);
        insertHistory(fixture, "REJECTED", null, null, superAdmin, Instant.now(), "reason");
        insertHistory(fixture, "TAKEDOWN", null, null, superAdmin, Instant.now(), "reason");

        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", null, null, null, Instant.now(), "reason"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", null, null, superAdmin, null, "reason"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", null, null, superAdmin, Instant.now(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", null, null, superAdmin, Instant.now(), "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", null, null, superAdmin, Instant.now(), "x".repeat(2001)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(
                fixture, "RESET", schoolAdmin, Instant.now(), superAdmin, Instant.now(), "reason"))
                .isInstanceOf(DataIntegrityViolationException.class);

        Fixture foreign = takedownFixture("PUBLISHED");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action, reviewer_id, reviewed_at, reason, created_at)
                VALUES (?, ?, ?, 'RESET', ?, now(), 'reason', now())
                """, UUID.randomUUID(), fixture.resultId(), foreign.publicVersionId(), superAdmin))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v023UpgradesExistingV022HistoryWithoutRewritingRows() {
        String schema = "d2b_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate isolated = new JdbcTemplate(dataSource);
        isolated.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("22"))
                    .load()
                    .migrate();

            UUID userId = UUID.randomUUID();
            UUID school = UUID.randomUUID();
            UUID activity = UUID.randomUUID();
            UUID result = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            isolated.update("INSERT INTO " + schema + ".users"
                            + "(id, username, password_hash, account_status, platform_role)"
                            + " VALUES (?, ?, '{noop}password', 'NORMAL', 'SUPER_ADMIN')",
                    userId, schema + "-user");
            isolated.update("INSERT INTO " + schema + ".schools("
                            + "id, name, unified_code_type, unified_code, internal_code, school_type,"
                            + " region, address, contact_name, contact_phone, contact_email, school_status)"
                            + " VALUES (?, ?, 'USCC', ?, ?, 'PRIMARY', 'Beijing', 'Address', 'Contact',"
                            + " '13800000000', ?, 'NORMAL')",
                    school, schema, schema.substring(0, 18) + "u", schema.substring(0, 18) + "i",
                    schema + "@example.com");
            isolated.update("INSERT INTO " + schema + ".activities("
                            + "id, school_id, title, execution_status, public_status, created_by)"
                            + " VALUES (?, ?, 'Activity', 'PUBLISHED', 'NOT_SUBMITTED', ?)",
                    activity, school, userId);
            isolated.update("INSERT INTO " + schema + ".activity_results("
                            + "id, school_id, activity_id, result_internal_status, result_public_status)"
                            + " VALUES (?, ?, ?, 'INTERNAL_PUBLISHED', 'PLATFORM_TAKEDOWN')",
                    result, school, activity);
            isolated.update("INSERT INTO " + schema + ".result_versions("
                            + "id, result_id, version_number, title, summary_text)"
                            + " VALUES (?, ?, 1, 'V1', 'Summary')",
                    version, result);

            insertIsolatedV022History(isolated, schema, result, version, userId,
                    "SUBMITTED", true, null);
            insertIsolatedV022History(isolated, schema, result, version, userId,
                    "APPROVED", false, null);
            insertIsolatedV022History(isolated, schema, result, version, userId,
                    "REJECTED", false, "rejected");
            insertIsolatedV022History(isolated, schema, result, version, userId,
                    "TAKEDOWN", false, "takedown");

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM " + schema + ".result_review_records",
                    Integer.class)).isEqualTo(4);
            isolated.update("INSERT INTO " + schema + ".result_review_records("
                            + "id, result_id, result_version_id, action, reviewer_id, reviewed_at, reason)"
                            + " VALUES (?, ?, ?, 'RESET', ?, now(), 'normalized reset')",
                    UUID.randomUUID(), result, version, userId);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM " + schema
                            + ".result_review_records WHERE action = 'RESET'",
                    Integer.class)).isOne();
        } finally {
            isolated.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Fixture fixture(String publicStatus, String executionStatus, boolean blocked) {
        UUID activityId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID publicVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, ?, 'NOT_SUBMITTED', ?)
                """, activityId, schoolId, prefix + "-activity-" + activityId,
                executionStatus, schoolAdmin);
        jdbc.update("""
                INSERT INTO activity_results(
                    id, school_id, activity_id, result_internal_status, result_public_status)
                VALUES (?, ?, ?, 'INTERNAL_PUBLISHED', ?)
                """, resultId, schoolId, activityId, publicStatus);
        insertVersion(publicVersionId, resultId, 1, "Public V1", Instant.parse("2026-09-18T08:00:00Z"));
        jdbc.update("""
                UPDATE activity_results
                SET current_internal_version_id = ?, current_public_version_id = ?,
                    current_candidate_version_id = NULL, public_visibility_blocked = ?
                WHERE id = ?
                """, publicVersionId, publicVersionId, blocked, resultId);
        return new Fixture(activityId, resultId, publicVersionId, publicVersionId);
    }

    private Fixture takedownFixture(String executionStatus) {
        Fixture fixture = fixture("PLATFORM_TAKEDOWN", executionStatus, true);
        insertHistory(fixture, "TAKEDOWN", null, null, superAdmin, Instant.now(), "original takedown");
        return fixture;
    }

    private UUID insertVersion(UUID resultId, int number, String title, Instant publiclyAt) {
        UUID id = UUID.randomUUID();
        insertVersion(id, resultId, number, title, publiclyAt);
        return id;
    }

    private void insertVersion(UUID id, UUID resultId, int number, String title, Instant publiclyAt) {
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights, media_refs,
                    published_internally_at, published_publicly_at)
                VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb, now(), ?)
                """, id, resultId, number, title, "Summary " + title,
                publiclyAt == null ? null : Timestamp.from(publiclyAt));
    }

    private void insertHistory(
            Fixture fixture,
            String action,
            UUID submittedBy,
            Instant submittedAt,
            UUID reviewerId,
            Instant reviewedAt,
            String reason) {
        jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action, submitted_by, submitted_at,
                    reviewer_id, reviewed_at, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), fixture.resultId(), fixture.publicVersionId(), action,
                submittedBy, timestamp(submittedAt), reviewerId, timestamp(reviewedAt), reason);
    }

    private static void insertIsolatedV022History(
            JdbcTemplate isolated,
            String schema,
            UUID resultId,
            UUID versionId,
            UUID actorId,
            String action,
            boolean submitted,
            String reason) {
        isolated.update("INSERT INTO " + schema + ".result_review_records("
                        + "id, result_id, result_version_id, action, submitted_by, submitted_at,"
                        + " reviewer_id, reviewed_at, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), resultId, versionId, action,
                submitted ? actorId : null,
                submitted ? Timestamp.from(Instant.now()) : null,
                submitted ? null : actorId,
                submitted ? null : Timestamp.from(Instant.now()),
                reason);
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
            return new RaceResult(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable execute(CountDownLatch ready, CountDownLatch start, ThrowingOperation operation) {
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

    private void resetInThread(UUID resultId, String reason) {
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        governanceService.resetTakedown(resultId, reason);
    }

    private void takedownInThread(UUID resultId, String reason) {
        authenticate(superAdmin, "SUPER_ADMIN", null, null);
        governanceService.takedown(resultId, reason);
    }

    private static void assertExactlyOneSuccess(RaceResult race) {
        int successes = (race.firstFailure() == null ? 1 : 0) + (race.secondFailure() == null ? 1 : 0);
        assertThat(successes).isOne();
        assertThat(race.failure()).isNotNull();
    }

    private static boolean isConcurrencyOrStateConflict(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current instanceof IllegalStateException) return true;
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
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
                : List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), actorSchoolId, membershipRole));
        var details = new CampusGuinnessUserDetails(
                userId, prefix + "-principal", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    private Map<String, Object> versionSnapshot(UUID versionId) {
        return jdbc.queryForMap("""
                SELECT version_number, title, summary_text, score_highlights, media_refs,
                       is_core_content_modified, format_change_log,
                       published_internally_at, published_publicly_at, created_at
                FROM result_versions WHERE id = ?
                """, versionId);
    }

    private String resultStatus(UUID resultId) {
        return jdbc.queryForObject(
                "SELECT result_public_status FROM activity_results WHERE id = ?", String.class, resultId);
    }

    private Object resultColumn(UUID resultId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM activity_results WHERE id = ?", Object.class, resultId);
    }

    private int historyCount(UUID resultId, String action) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM result_review_records WHERE result_id = ? AND action = ?
                """, Integer.class, resultId, action);
    }

    private String historyReason(UUID resultId, String action) {
        return jdbc.queryForObject("""
                SELECT reason FROM result_review_records WHERE result_id = ? AND action = ?
                """, String.class, resultId, action);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
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
            UUID internalVersionId) {
    }
}
