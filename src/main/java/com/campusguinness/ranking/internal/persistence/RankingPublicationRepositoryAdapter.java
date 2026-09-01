package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.RankingPublicationRepository;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
@Transactional
class RankingPublicationRepositoryAdapter implements RankingPublicationRepository {
    private final JdbcTemplate jdbc;

    RankingPublicationRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RankingPublicationResult publishGeneratedVersion(RankingDefinition definition, UUID rankingVersionId) {
        UUID definitionId = definition.id().value();
        UUID currentVersionId = jdbc.queryForObject("""
                SELECT current_version_id
                FROM ranking_definitions
                WHERE id = ?
                FOR UPDATE
                """, UUID.class, definitionId);
        VersionRow target = lockVersion(rankingVersionId);
        if (!definitionId.equals(target.definitionId())) {
            throw new IllegalStateException("Cannot publish ranking: version does not belong to definition.");
        }
        if (!"GENERATED".equals(target.status())) {
            throw new IllegalStateException("Cannot publish ranking: only GENERATED versions can be published.");
        }

        if (currentVersionId != null) {
            VersionRow current = lockVersion(currentVersionId);
            if (!definitionId.equals(current.definitionId()) || !"PUBLISHED".equals(current.status())) {
                throw new IllegalStateException("Cannot publish ranking: current version state is inconsistent.");
            }
            if (!currentVersionId.equals(rankingVersionId)) {
                requireUpdated("current ranking version replacement", jdbc.update("""
                        UPDATE ranking_versions
                        SET version_status = 'REPLACED'
                        WHERE id = ? AND definition_id = ? AND version_status = 'PUBLISHED'
                        """, currentVersionId, definitionId));
            }
        }

        Instant publishedAt = Instant.now();
        requireUpdated("ranking version publication", jdbc.update("""
                UPDATE ranking_versions
                SET version_status = 'PUBLISHED', published_at = ?
                WHERE id = ? AND definition_id = ? AND version_status = 'GENERATED'
                """, Timestamp.from(publishedAt), rankingVersionId, definitionId));
        requireUpdated("ranking definition current version switch", jdbc.update("""
                UPDATE ranking_definitions
                SET current_version_id = ?, updated_at = ?
                WHERE id = ?
                """, rankingVersionId, Timestamp.from(publishedAt), definitionId));
        return new RankingPublicationResult(
                definitionId, rankingVersionId, currentVersionId, rankingVersionId, "PUBLISHED", publishedAt);
    }

    private VersionRow lockVersion(UUID versionId) {
        return jdbc.query("""
                SELECT id, definition_id, version_status
                FROM ranking_versions
                WHERE id = ?
                FOR UPDATE
                """, rs -> {
            if (!rs.next()) {
                throw new IllegalArgumentException("RankingVersion not found: " + versionId);
            }
            return new VersionRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("definition_id", UUID.class),
                    rs.getString("version_status"));
        }, versionId);
    }

    private void requireUpdated(String action, int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("Cannot publish ranking: " + action + " updated " + updatedRows + " rows.");
        }
    }

    private record VersionRow(UUID id, UUID definitionId, String status) {}
}
