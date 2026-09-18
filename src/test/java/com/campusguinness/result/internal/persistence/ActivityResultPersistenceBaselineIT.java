package com.campusguinness.result.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultInternalStatus;
import com.campusguinness.result.internal.domain.ResultPublicStatus;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityResultPersistenceBaselineIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ActivityResultRepository activityResults;
    @Autowired private ResultVersionRepository resultVersions;

    private UUID userId;
    private UUID schoolId;

    @AfterEach
    void cleanTestData() {
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id = ?)", schoolId);
        jdbc.update("DELETE FROM activity_results WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM activities WHERE school_id = ?", schoolId);
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @BeforeEach
    void createPrerequisites() {
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                userId, "result_" + shortId(), "$2a$10$test", "NORMAL");
        jdbc.update("INSERT INTO schools(id, name, unified_code_type, unified_code, internal_code, "
                        + "school_type, region, address, contact_name, contact_phone, contact_email, school_status) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "Result Test School", "USCC", "RESULT-" + shortId(), "R-" + shortId(),
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000", "result@test.com", "NORMAL");
    }

    @Test
    void migrationAddsCandidatePointerVisibilityBlockAndSafeDefaults() {
        Integer candidateColumn = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'activity_results'
                  AND column_name = 'current_candidate_version_id'
                """, Integer.class);
        String blockedDefault = jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'activity_results'
                  AND column_name = 'public_visibility_blocked'
                """, String.class);
        String blockedNullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'activity_results'
                  AND column_name = 'public_visibility_blocked'
                """, String.class);
        Integer candidateForeignKey = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = 'activity_results'
                  AND constraint_name = 'fk_result_current_candidate_version'
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        assertThat(candidateColumn).isOne();
        assertThat(blockedDefault).isEqualTo("false");
        assertThat(blockedNullable).isEqualTo("NO");
        assertThat(candidateForeignKey).isOne();

        UUID activityId = createActivity();
        UUID resultId = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_results(id, school_id, activity_id) VALUES (?,?,?)",
                resultId, schoolId, activityId);
        assertThat(jdbc.queryForObject(
                "SELECT current_candidate_version_id FROM activity_results WHERE id = ?",
                UUID.class, resultId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT public_visibility_blocked FROM activity_results WHERE id = ?",
                Boolean.class, resultId)).isFalse();
    }

    @Test
    void activityWithoutResultRemainsValid() {
        UUID activityId = createActivity();
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM activity_results WHERE activity_id = ?", Integer.class, activityId);
        assertThat(count).isZero();
    }

    @Test
    void candidatePointerRoundTripsFromFirstSnapshot() {
        ActivityResult result = createResult(createActivity());
        ResultVersion v1 = createVersion(result, 1, "V1");

        activityResults.save(withPointers(result, v1.id().value(), null, null, false));
        ActivityResult reloaded = reload(result);

        assertThat(reloaded.currentCandidateVersionId()).isEqualTo(v1.id().value());
        assertThat(reloaded.currentInternalVersionId()).isNull();
        assertThat(reloaded.currentPublicVersionId()).isNull();
        assertThat(reloaded.publicVisibilityBlocked()).isFalse();
    }

    @Test
    void allThreePointersRoundTripIndependently() {
        ActivityResult result = createResult(createActivity());
        ResultVersion v1 = createVersion(result, 1, "V1");
        ResultVersion v2 = createVersion(result, 2, "V2");
        ResultVersion v3 = createVersion(result, 3, "V3");

        activityResults.save(withPointers(result,
                v3.id().value(), v2.id().value(), v1.id().value(), false));
        ActivityResult reloaded = reload(result);

        assertThat(reloaded.currentCandidateVersionId()).isEqualTo(v3.id().value());
        assertThat(reloaded.currentInternalVersionId()).isEqualTo(v2.id().value());
        assertThat(reloaded.currentPublicVersionId()).isEqualTo(v1.id().value());
        assertThat(reloaded.publicVisibilityBlocked()).isFalse();
    }

    @Test
    void publicPointerAndVisibilityBlockRoundTripIndependently() {
        ActivityResult result = createResult(createActivity());
        ResultVersion v1 = createVersion(result, 1, "V1");

        activityResults.save(withPointers(result, null, null, v1.id().value(), true));
        ActivityResult reloaded = reload(result);

        assertThat(reloaded.currentPublicVersionId()).isEqualTo(v1.id().value());
        assertThat(reloaded.publicVisibilityBlocked()).isTrue();
    }

    @Test
    void staleAggregateSaveRaisesRealOptimisticLockConflict() {
        ActivityResult original = createResult(createActivity());
        ResultVersion v1 = createVersion(original, 1, "V1");
        activityResults.save(withPointers(original, v1.id().value(), null, null, false));
        ActivityResult copyA = reload(original);
        ActivityResult copyB = reload(original);

        copyA.publishInternal(v1.id().value());
        copyB.publishInternal(v1.id().value());
        activityResults.save(copyA);

        assertThatThrownBy(() -> activityResults.save(copyB))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Row was updated or deleted by another transaction");
    }

    @ParameterizedTest
    @ValueSource(strings = {"candidate", "internal", "public"})
    void persistenceInvariantRejectsCrossResultPointers(String pointer) {
        ActivityResult resultA = createResult(createActivity());
        ActivityResult resultB = createResult(createActivity());
        ResultVersion b1 = createVersion(resultB, 1, "B1");

        ActivityResult invalid = switch (pointer) {
            case "candidate" -> withPointers(resultA, b1.id().value(), null, null, false);
            case "internal" -> withPointers(resultA, null, b1.id().value(), null, false);
            case "public" -> withPointers(resultA, null, null, b1.id().value(), false);
            default -> throw new IllegalArgumentException(pointer);
        };

        assertThatThrownBy(() -> activityResults.save(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same ActivityResult");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "current_candidate_version_id",
            "current_internal_version_id",
            "current_public_version_id"
    })
    void databaseForeignKeysRejectCrossResultPointers(String column) {
        ActivityResult resultA = createResult(createActivity());
        ActivityResult resultB = createResult(createActivity());
        ResultVersion b1 = createVersion(resultB, 1, "B1");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE activity_results SET " + column + " = ? WHERE id = ?",
                b1.id().value(), resultA.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resultVersionRepositoryCreatesAndLoadsImmutableSnapshot() {
        ActivityResult result = createResult(createActivity());
        ResultVersion original = createVersion(result, 1, "Original");

        ResultVersion loaded = resultVersions.findById(original.id()).orElseThrow();
        assertThat(loaded.title()).isEqualTo("Original");
        assertThat(loaded.resultId()).isEqualTo(result.id());
        assertThat(loaded.versionNumber()).isOne();

        ResultVersion replacementAttempt = ResultVersion.create(new ResultVersion.Builder()
                .id(original.id())
                .resultId(result.id())
                .versionNumber(1)
                .title("Mutated")
                .summaryText("Must not replace V1"));

        assertThatThrownBy(() -> resultVersions.create(replacementAttempt))
                .isInstanceOf(RuntimeException.class);
        assertThat(resultVersions.findById(original.id()).orElseThrow().title()).isEqualTo("Original");
    }

    @Test
    void internalPublicationStampPreservesCoreSnapshot() {
        ActivityResult result = createResult(createActivity());
        ResultVersion before = createVersion(result, 1, "Original");
        Instant publishedAt = Instant.parse("2026-09-16T12:00:00Z");

        resultVersions.markPublishedInternally(before.id(), publishedAt);

        ResultVersion after = resultVersions.findById(before.id()).orElseThrow();
        assertThat(after.publishedInternallyAt()).isEqualTo(publishedAt);
        assertThat(after.resultId()).isEqualTo(before.resultId());
        assertThat(after.versionNumber()).isEqualTo(before.versionNumber());
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.summaryText()).isEqualTo(before.summaryText());
        assertThat(after.scoreHighlights()).isEqualTo(before.scoreHighlights());
        assertThat(after.mediaRefs()).isEqualTo(before.mediaRefs());
        assertThat(after.coreContentModified()).isEqualTo(before.coreContentModified());
        assertThat(after.formatChangeLog()).isEqualTo(before.formatChangeLog());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
    }

    @Test
    void internalPublicationStampTargetsExactVersionOnly() {
        ActivityResult result = createResult(createActivity());
        ResultVersion v1 = createVersion(result, 1, "V1");
        ResultVersion v2 = createVersion(result, 2, "V2");
        Instant publishedAt = Instant.parse("2026-09-16T12:01:00Z");

        resultVersions.markPublishedInternally(v2.id(), publishedAt);

        assertThat(resultVersions.findById(v1.id()).orElseThrow().publishedInternallyAt()).isNull();
        assertThat(resultVersions.findById(v2.id()).orElseThrow().publishedInternallyAt())
                .isEqualTo(publishedAt);
    }

    @Test
    void internalPublicationStampCannotBeOverwritten() {
        ActivityResult result = createResult(createActivity());
        ResultVersion version = createVersion(result, 1, "V1");
        Instant firstStamp = Instant.parse("2026-09-16T12:02:00Z");
        Instant replacementStamp = Instant.parse("2026-09-16T12:03:00Z");
        resultVersions.markPublishedInternally(version.id(), firstStamp);

        assertThatThrownBy(() -> resultVersions.markPublishedInternally(version.id(), replacementStamp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published internally");
        assertThat(resultVersions.findById(version.id()).orElseThrow().publishedInternallyAt())
                .isEqualTo(firstStamp);
    }

    @Test
    void internalPublicationStampRejectsMissingVersion() {
        ResultVersionId missingId = new ResultVersionId(UUID.randomUUID());

        assertThatThrownBy(() -> resultVersions.markPublishedInternally(
                missingId, Instant.parse("2026-09-16T12:04:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void publicPublicationStampTargetsExactVersionAndPreservesCoreSnapshot() {
        ActivityResult result = createResult(createActivity());
        ResultVersion v1 = createVersion(result, 1, "V1");
        ResultVersion v2 = createVersion(result, 2, "V2");
        Instant publishedAt = Instant.parse("2026-09-17T12:00:00Z");

        resultVersions.markPublishedPublicly(v2.id(), publishedAt);

        ResultVersion after = resultVersions.findById(v2.id()).orElseThrow();
        assertThat(after.publishedPubliclyAt()).isEqualTo(publishedAt);
        assertThat(after.resultId()).isEqualTo(v2.resultId());
        assertThat(after.versionNumber()).isEqualTo(v2.versionNumber());
        assertThat(after.title()).isEqualTo(v2.title());
        assertThat(after.summaryText()).isEqualTo(v2.summaryText());
        assertThat(after.scoreHighlights()).isEqualTo(v2.scoreHighlights());
        assertThat(after.mediaRefs()).isEqualTo(v2.mediaRefs());
        assertThat(after.publishedInternallyAt()).isEqualTo(v2.publishedInternallyAt());
        assertThat(after.createdAt()).isEqualTo(v2.createdAt());
        assertThat(resultVersions.findById(v1.id()).orElseThrow().publishedPubliclyAt()).isNull();
    }

    @Test
    void publicPublicationStampCannotBeOverwritten() {
        ActivityResult result = createResult(createActivity());
        ResultVersion version = createVersion(result, 1, "V1");
        Instant firstStamp = Instant.parse("2026-09-17T12:01:00Z");
        resultVersions.markPublishedPublicly(version.id(), firstStamp);

        assertThatThrownBy(() -> resultVersions.markPublishedPublicly(
                version.id(), Instant.parse("2026-09-17T12:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published publicly");
        assertThat(resultVersions.findById(version.id()).orElseThrow().publishedPubliclyAt())
                .isEqualTo(firstStamp);
    }

    @Test
    void publicPublicationStampRejectsMissingVersion() {
        assertThatThrownBy(() -> resultVersions.markPublishedPublicly(
                new ResultVersionId(UUID.randomUUID()), Instant.parse("2026-09-17T12:03:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    private ActivityResult createResult(UUID activityId) {
        ActivityResult result = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID()))
                .schoolId(schoolId)
                .activityId(activityId));
        activityResults.save(result);
        return reload(result);
    }

    private ResultVersion createVersion(ActivityResult result, int number, String title) {
        ResultVersion version = ResultVersion.create(new ResultVersion.Builder()
                .id(new ResultVersionId(UUID.randomUUID()))
                .resultId(result.id())
                .versionNumber(number)
                .title(title)
                .summaryText(title + " summary")
                .scoreHighlights("{\"rank\":" + number + "}")
                .mediaRefs("[]"));
        resultVersions.create(version);
        return resultVersions.findById(version.id()).orElseThrow();
    }

    private ActivityResult reload(ActivityResult result) {
        return activityResults.findById(result.id()).orElseThrow();
    }

    private ActivityResult withPointers(
            ActivityResult source,
            UUID candidate,
            UUID internal,
            UUID published,
            boolean blocked) {
        return ActivityResult.reconstitute(new ActivityResult.Builder()
                        .id(source.id()).schoolId(source.schoolId()).activityId(source.activityId()),
                source.internalStatus(), source.publicStatus(), candidate, internal, published,
                blocked, source.createdAt(), source.updatedAt(), source.persistenceVersion());
    }

    private UUID createActivity() {
        UUID activityId = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) "
                        + "VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "Result Activity", "DRAFT", "NOT_SUBMITTED", userId);
        return activityId;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
