package com.campusguinness.result.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.format.ResultFormatPresentationValidator;
import com.campusguinness.result.application.query.ActivityResultReadConsistencyException;
import com.campusguinness.result.application.query.model.ActivityResultVersionProjection;
import com.campusguinness.result.application.query.model.GovernanceActivityResultDetail;
import com.campusguinness.result.application.query.model.GovernanceActivityResultHistoryEntry;
import com.campusguinness.result.application.query.model.GovernanceActivityResultSummary;
import com.campusguinness.result.application.query.port.ActivityResultGovernanceQueryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ActivityResultGovernanceQueryAdapter implements ActivityResultGovernanceQueryPort {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResultFormatPresentationValidator presentationValidator;

    ActivityResultGovernanceQueryAdapter(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ResultFormatPresentationValidator presentationValidator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.presentationValidator = presentationValidator;
    }

    @Override
    public QueryPage<GovernanceActivityResultSummary> findGovernance(
            int page, int size, String publicStatus, Boolean blocked, String query) {
        StringBuilder where = new StringBuilder(" WHERE ar.current_public_version_id IS NOT NULL");
        List<Object> args = new ArrayList<>();
        if (publicStatus != null) {
            where.append(" AND ar.result_public_status = ?");
            args.add(publicStatus);
        }
        if (blocked != null) {
            where.append(" AND ar.public_visibility_blocked = ?");
            args.add(blocked);
        }
        if (query != null) {
            where.append(" AND (LOWER(s.name) LIKE ? OR LOWER(a.title) LIKE ?)");
            String pattern = "%" + query + "%";
            args.add(pattern);
            args.add(pattern);
        }

        String from = """
                FROM activity_results ar
                JOIN activities a ON a.id = ar.activity_id AND a.school_id = ar.school_id
                JOIN schools s ON s.id = ar.school_id
                """;
        String select = """
                SELECT ar.id AS result_id, ar.school_id, ar.activity_id,
                       s.name AS school_name, a.title AS activity_title,
                       a.execution_status AS activity_execution_status,
                       ar.result_internal_status, ar.result_public_status,
                       ar.public_visibility_blocked, ar.current_public_version_id,
                       ar.updated_at
                """ + from + where + " ORDER BY ar.updated_at DESC, ar.id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<GovernanceActivityResultSummary> items = jdbc.query(
                select, this::mapSummary, pageArgs.toArray());

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<GovernanceActivityResultDetail> findGovernanceById(UUID resultId) {
        return jdbc.query("""
                SELECT ar.id AS result_id, ar.school_id, ar.activity_id,
                       s.name AS school_name, a.title AS activity_title,
                       a.execution_status AS activity_execution_status,
                       ar.result_internal_status, ar.result_public_status,
                       ar.public_visibility_blocked,
                       ar.current_candidate_version_id, ar.current_internal_version_id,
                       ar.current_public_version_id, ar.updated_at,
                       pv.id AS public_version_id, pv.version_number AS public_version_number,
                       pv.title AS public_title, pv.summary_text AS public_summary_text,
                       pv.score_highlights::text AS public_score_highlights,
                       pv.media_refs::text AS public_media_refs,
                       pv.published_internally_at AS public_published_internally_at,
                       pv.published_publicly_at AS public_published_publicly_at,
                       pvf.payload::text AS public_format_payload,
                       pvf.id AS public_format_record_id,
                       pvf.revision AS public_format_revision
                FROM activity_results ar
                JOIN activities a ON a.id = ar.activity_id AND a.school_id = ar.school_id
                JOIN schools s ON s.id = ar.school_id
                LEFT JOIN result_versions pv
                  ON pv.id = ar.current_public_version_id
                 AND pv.result_id = ar.id
                LEFT JOIN result_format_heads pvh
                  ON pvh.result_version_id = pv.id AND pvh.result_id = ar.id
                LEFT JOIN result_format_edit_records pvf
                  ON pvf.id = pvh.current_format_edit_record_id
                 AND pvf.result_id = ar.id AND pvf.result_version_id = pv.id
                WHERE ar.id = ?
                  AND ar.current_public_version_id IS NOT NULL
                """, rs -> rs.next() ? Optional.of(mapDetail(rs)) : Optional.empty(), resultId);
    }

    private GovernanceActivityResultSummary mapSummary(ResultSet rs, int row) throws SQLException {
        return new GovernanceActivityResultSummary(
                uuid(rs, "result_id"), uuid(rs, "school_id"), uuid(rs, "activity_id"),
                rs.getString("school_name"), rs.getString("activity_title"),
                rs.getString("activity_execution_status"), rs.getString("result_internal_status"),
                rs.getString("result_public_status"), rs.getBoolean("public_visibility_blocked"),
                uuid(rs, "current_public_version_id"), instant(rs, "updated_at"));
    }

    private GovernanceActivityResultDetail mapDetail(ResultSet rs) throws SQLException {
        UUID resultId = uuid(rs, "result_id");
        UUID pointer = uuid(rs, "current_public_version_id");
        UUID exactVersion = uuid(rs, "public_version_id");
        if (pointer == null || exactVersion == null || !pointer.equals(exactVersion)) {
            throw new ActivityResultReadConsistencyException(
                    "ActivityResult public pointer does not reference its exact current version");
        }
        return new GovernanceActivityResultDetail(
                resultId, uuid(rs, "school_id"), uuid(rs, "activity_id"),
                rs.getString("school_name"), rs.getString("activity_title"),
                rs.getString("activity_execution_status"), rs.getString("result_internal_status"),
                rs.getString("result_public_status"), rs.getBoolean("public_visibility_blocked"),
                uuid(rs, "current_candidate_version_id"), uuid(rs, "current_internal_version_id"), pointer,
                mapPublicVersion(rs), findGovernanceHistory(resultId), instant(rs, "updated_at"));
    }

    private ActivityResultVersionProjection mapPublicVersion(ResultSet rs) throws SQLException {
        return new ActivityResultVersionProjection(
                uuid(rs, "public_version_id"), rs.getInt("public_version_number"),
                rs.getString("public_title"), rs.getString("public_summary_text"),
                readList(rs.getString("public_score_highlights"), STRING_LIST, "scoreHighlights"),
                readList(rs.getString("public_media_refs"), UUID_LIST, "mediaRefs"),
                instant(rs, "public_published_internally_at"),
                instant(rs, "public_published_publicly_at"),
                restorePresentation(rs.getString("public_format_payload"), rs.getString("public_summary_text")),
                uuid(rs, "public_format_record_id"), integer(rs, "public_format_revision"));
    }

    private List<GovernanceActivityResultHistoryEntry> findGovernanceHistory(UUID resultId) {
        return jdbc.query("""
                SELECT id, result_version_id, action, reviewer_id, reviewed_at, reason, created_at
                FROM result_review_records
                WHERE result_id = ? AND action IN ('TAKEDOWN', 'RESET')
                ORDER BY created_at ASC, id ASC
                """, (rs, row) -> new GovernanceActivityResultHistoryEntry(
                uuid(rs, "id"), uuid(rs, "result_version_id"), rs.getString("action"),
                uuid(rs, "reviewer_id"), instant(rs, "reviewed_at"), rs.getString("reason"),
                instant(rs, "created_at")), resultId);
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type, String field) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.copyOf(objectMapper.readValue(json, type));
        } catch (JsonProcessingException ex) {
            throw new ActivityResultReadConsistencyException("Stored " + field + " are invalid");
        }
    }

    private com.campusguinness.result.application.format.ResultFormatPresentation restorePresentation(
            String json, String summary) {
        if (json == null || json.isBlank()) return null;
        return presentationValidator.restoreAndValidate(json, summary);
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
