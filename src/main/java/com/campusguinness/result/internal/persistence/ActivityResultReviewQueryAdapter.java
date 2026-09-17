package com.campusguinness.result.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.PendingResultReviewDetail;
import com.campusguinness.result.application.query.model.PendingResultReviewSummary;
import com.campusguinness.result.application.query.model.ResultReviewHistoryEntry;
import com.campusguinness.result.application.query.port.ActivityResultReviewQueryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ActivityResultReviewQueryAdapter implements ActivityResultReviewQueryPort {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    ActivityResultReviewQueryAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public QueryPage<PendingResultReviewSummary> findPending(int page, int size) {
        List<PendingResultReviewSummary> items = jdbc.query("""
                SELECT ar.id AS result_id, ar.school_id, ar.activity_id,
                       rv.id AS candidate_version_id, rv.version_number,
                       rv.title, submitted.submitted_at, submitted.submitted_by,
                       ar.result_public_status
                FROM activity_results ar
                JOIN result_versions rv
                  ON rv.id = ar.current_candidate_version_id
                 AND rv.result_id = ar.id
                JOIN LATERAL (
                    SELECT rr.action, rr.submitted_at, rr.submitted_by
                    FROM result_review_records rr
                    WHERE rr.result_id = ar.id
                      AND rr.result_version_id = rv.id
                    ORDER BY rr.created_at DESC, rr.id DESC
                    LIMIT 1
                ) submitted ON true
                WHERE ar.result_public_status = 'PENDING_PUBLIC_REVIEW'
                  AND submitted.action = 'SUBMITTED'
                ORDER BY submitted.submitted_at ASC, ar.id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, row) -> new PendingResultReviewSummary(
                        uuid(rs, "result_id"),
                        uuid(rs, "school_id"),
                        uuid(rs, "activity_id"),
                        uuid(rs, "candidate_version_id"),
                        rs.getInt("version_number"),
                        rs.getString("title"),
                        instant(rs, "submitted_at"),
                        uuid(rs, "submitted_by"),
                        rs.getString("result_public_status")),
                size,
                page * size);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM activity_results ar
                JOIN result_versions rv
                  ON rv.id = ar.current_candidate_version_id
                 AND rv.result_id = ar.id
                WHERE ar.result_public_status = 'PENDING_PUBLIC_REVIEW'
                  AND (
                      SELECT rr.action
                      FROM result_review_records rr
                      WHERE rr.result_id = ar.id
                        AND rr.result_version_id = rv.id
                      ORDER BY rr.created_at DESC, rr.id DESC
                      LIMIT 1
                  ) = 'SUBMITTED'
                """, Long.class);
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<PendingResultReviewDetail> findPendingDetail(UUID resultId) {
        return jdbc.query("""
                SELECT ar.id AS result_id, ar.school_id, ar.activity_id,
                       rv.id AS candidate_version_id, rv.version_number,
                       rv.title, rv.summary_text, rv.score_highlights::text AS score_highlights,
                       rv.media_refs::text AS media_refs,
                       submitted.submitted_at, submitted.submitted_by,
                       ar.result_public_status
                FROM activity_results ar
                JOIN result_versions rv
                  ON rv.id = ar.current_candidate_version_id
                 AND rv.result_id = ar.id
                JOIN LATERAL (
                    SELECT rr.action, rr.submitted_at, rr.submitted_by
                    FROM result_review_records rr
                    WHERE rr.result_id = ar.id
                      AND rr.result_version_id = rv.id
                    ORDER BY rr.created_at DESC, rr.id DESC
                    LIMIT 1
                ) submitted ON true
                WHERE ar.id = ?
                  AND ar.result_public_status = 'PENDING_PUBLIC_REVIEW'
                  AND submitted.action = 'SUBMITTED'
                """,
                rs -> rs.next() ? Optional.of(mapDetail(rs)) : Optional.empty(),
                resultId);
    }

    private PendingResultReviewDetail mapDetail(ResultSet rs) throws SQLException {
        UUID resultId = uuid(rs, "result_id");
        UUID candidateVersionId = uuid(rs, "candidate_version_id");
        return new PendingResultReviewDetail(
                resultId,
                uuid(rs, "school_id"),
                uuid(rs, "activity_id"),
                candidateVersionId,
                rs.getInt("version_number"),
                rs.getString("title"),
                rs.getString("summary_text"),
                readList(rs.getString("score_highlights"), STRING_LIST, "scoreHighlights"),
                readList(rs.getString("media_refs"), UUID_LIST, "mediaRefs"),
                instant(rs, "submitted_at"),
                uuid(rs, "submitted_by"),
                rs.getString("result_public_status"),
                findHistory(resultId, candidateVersionId));
    }

    private List<ResultReviewHistoryEntry> findHistory(UUID resultId, UUID versionId) {
        return jdbc.query("""
                SELECT id, result_version_id, action, submitted_by, submitted_at,
                       reviewer_id, reviewed_at, reason, created_at
                FROM result_review_records
                WHERE result_id = ? AND result_version_id = ?
                ORDER BY created_at ASC, id ASC
                """,
                (rs, row) -> new ResultReviewHistoryEntry(
                        uuid(rs, "id"),
                        uuid(rs, "result_version_id"),
                        rs.getString("action"),
                        uuid(rs, "submitted_by"),
                        instant(rs, "submitted_at"),
                        uuid(rs, "reviewer_id"),
                        instant(rs, "reviewed_at"),
                        rs.getString("reason"),
                        instant(rs, "created_at")),
                resultId,
                versionId);
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type, String field) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.copyOf(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored " + field + " are invalid", e);
        }
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
