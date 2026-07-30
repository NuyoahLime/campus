package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPublicationAdapterIT extends RankingIntegrationTestSupport {

    @Autowired SchoolAdminRankingApplicationService service;

    @Test
    void publicationPersistsDefinitionVersionEntriesAndSources() {
        var published = publish();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ranking_definitions WHERE activity_project_id=?",
                Long.class,
                activityProjectId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ranking_entries WHERE version_id=?",
                Long.class,
                published.versionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_entry_score_sources source
                JOIN ranking_entries entry ON entry.id=source.entry_id
                WHERE entry.version_id=?
                """, Long.class, published.versionId())).isEqualTo(1);
    }

    @Test
    void publishedSnapshotContainsAllCalculationParameters() {
        var published = publish();

        String snapshot = jdbc.queryForObject(
                "SELECT calculation_params::text FROM ranking_versions WHERE id=?",
                String.class,
                published.versionId());
        assertThat(snapshot)
                .contains("\"scoreStorageType\"")
                .contains("\"comparisonDirection\"")
                .contains("\"effectiveScoreRule\"")
                .contains("\"tiePolicy\"")
                .contains("\"sourceFingerprint\"")
                .contains("\"currentRuleVersionId\"");
    }

    @Test
    void dataScopeAndEmptyAuthorizationSnapshotArePersisted() {
        var published = publish();

        var row = jdbc.queryForMap("""
                SELECT data_scope_snapshot::text AS scope,
                       authorization_ids_snapshot::text AS authorizations
                FROM ranking_versions WHERE id=?
                """, published.versionId());
        assertThat((String) row.get("scope"))
                .contains(activityProjectId.toString())
                .contains(schoolId.toString())
                .contains("\"includedStudentCount\"");
        assertThat(row.get("authorizations")).isEqualTo("[]");
    }

    @Test
    void definitionCurrentPointerTargetsPublishedVersion() {
        var published = publish();

        assertThat(jdbc.queryForObject("""
                SELECT current_version_id
                FROM ranking_definitions
                WHERE activity_project_id=?
                """, UUID.class, activityProjectId))
                .isEqualTo(published.versionId());
    }

    @Test
    void repeatedPublicationCreatesSequentialImmutableVersion() {
        var first = publish();
        var second = publish();

        assertThat(first.versionNumber()).isEqualTo(1);
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT version_status FROM ranking_versions WHERE id=?",
                String.class,
                first.versionId())).isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ranking_entries WHERE version_id=?",
                Long.class,
                first.versionId())).isEqualTo(1);
    }

    @Test
    void everyEntryHasExactlyOneRelationalScoreSource() {
        var published = publish();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_entries entry
                WHERE entry.version_id=?
                  AND 1 = (
                    SELECT COUNT(*)
                    FROM ranking_entry_score_sources source
                    WHERE source.entry_id=entry.id)
                """, Long.class, published.versionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT source.score_attempt_id
                FROM ranking_entry_score_sources source
                JOIN ranking_entries entry ON entry.id=source.entry_id
                WHERE entry.version_id=?
                """, UUID.class, published.versionId())).isEqualTo(scoreAttemptId);
    }

    private com.campusguinness.ranking.application.query.model.RankingVersionDetail publish() {
        String fingerprint = service.preview(adminId, activityProjectId)
                .sourceFingerprint();
        return service.publish(adminId, activityProjectId, fingerprint);
    }
}
