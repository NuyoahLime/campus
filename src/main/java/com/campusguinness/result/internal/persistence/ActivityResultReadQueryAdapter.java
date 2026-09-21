package com.campusguinness.result.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.ActivityResultReadConsistencyException;
import com.campusguinness.result.application.query.model.ActivityResultHistoryEntry;
import com.campusguinness.result.application.query.model.ActivityResultStudentReadState;
import com.campusguinness.result.application.query.model.ActivityResultVersionProjection;
import com.campusguinness.result.application.query.model.ManagementActivityResultDetail;
import com.campusguinness.result.application.query.model.ManagementActivityResultSummary;
import com.campusguinness.result.application.query.model.PublicActivityResultView;
import com.campusguinness.result.application.format.ResultFormatPresentationValidator;
import com.campusguinness.result.application.query.model.ResultFormatOverlayProjection;
import com.campusguinness.result.application.query.port.ActivityResultReadQueryPort;
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
class ActivityResultReadQueryAdapter implements ActivityResultReadQueryPort {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResultFormatPresentationValidator presentationValidator;

    ActivityResultReadQueryAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper,
            ResultFormatPresentationValidator presentationValidator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.presentationValidator = presentationValidator;
    }

    @Override
    public Optional<PublicActivityResultView> findPublicByActivityId(UUID activityId) {
        return jdbc.query("""
                SELECT a.id AS activity_id, ar.id AS result_id,
                       pv.id AS version_id, pv.version_number, pv.title, pv.summary_text,
                       pv.score_highlights::text AS score_highlights,
                       pv.published_publicly_at, fe.payload::text AS format_payload
                FROM activities a
                JOIN activity_results ar ON ar.activity_id = a.id
                JOIN result_versions pv
                  ON pv.id = ar.current_public_version_id
                 AND pv.result_id = ar.id
                LEFT JOIN result_format_heads fh ON fh.result_version_id = pv.id AND fh.result_id = ar.id
                LEFT JOIN result_format_edit_records fe ON fe.id = fh.current_format_edit_record_id
                 AND fe.result_id = ar.id AND fe.result_version_id = pv.id
                WHERE a.id = ?
                  AND a.execution_status IN ('PUBLISHED', 'IN_PROGRESS', 'ENDED', 'CANCELLED')
                  AND ar.current_public_version_id IS NOT NULL
                  AND ar.public_visibility_blocked = false
                  AND ar.result_public_status NOT IN ('ANOMALY_PENDING', 'PLATFORM_TAKEDOWN')
                """, rs -> rs.next() ? Optional.of(mapPublic(rs)) : Optional.empty(), activityId);
    }

    @Override
    public Optional<ActivityResultStudentReadState> findStudentState(UUID activityId, UUID schoolId) {
        return jdbc.query("""
                SELECT a.id AS activity_id, a.execution_status, ar.id AS result_id,
                       ar.result_internal_status, ar.result_public_status,
                       ar.current_candidate_version_id, ar.current_internal_version_id,
                       ar.current_public_version_id, ar.public_visibility_blocked,
                       iv.id AS internal_version_id, iv.version_number AS internal_version_number,
                       iv.title AS internal_title, iv.summary_text AS internal_summary_text,
                       iv.score_highlights::text AS internal_score_highlights,
                       iv.published_internally_at AS internal_published_internally_at,
                       iv.published_publicly_at AS internal_published_publicly_at,
                       pv.id AS public_version_id, pv.version_number AS public_version_number,
                       pv.title AS public_title, pv.summary_text AS public_summary_text,
                       pv.score_highlights::text AS public_score_highlights,
                       pv.published_internally_at AS public_published_internally_at,
                       pv.published_publicly_at AS public_published_publicly_at
                FROM activities a
                JOIN activity_results ar
                  ON ar.activity_id = a.id
                 AND ar.school_id = a.school_id
                LEFT JOIN result_versions iv
                  ON iv.id = ar.current_internal_version_id
                 AND iv.result_id = ar.id
                LEFT JOIN result_versions pv
                  ON pv.id = ar.current_public_version_id
                 AND pv.result_id = ar.id
                WHERE a.id = ?
                  AND a.school_id = ?
                  AND a.execution_status IN ('PUBLISHED', 'IN_PROGRESS', 'ENDED', 'CANCELLED')
                """, rs -> rs.next() ? Optional.of(mapStudentState(rs)) : Optional.empty(), activityId, schoolId);
    }

    @Override
    public Optional<ResultFormatOverlayProjection> findFormatOverlay(UUID resultId, UUID resultVersionId) {
        return jdbc.query("""
                SELECT fe.id, fe.revision, fe.payload::text, v.summary_text
                FROM result_format_heads fh
                JOIN result_format_edit_records fe
                  ON fe.id = fh.current_format_edit_record_id
                 AND fe.result_id = fh.result_id
                 AND fe.result_version_id = fh.result_version_id
                JOIN result_versions v
                  ON v.id = fh.result_version_id
                 AND v.result_id = fh.result_id
                WHERE fh.result_id = ? AND fh.result_version_id = ?
                """, rs -> rs.next() ? Optional.of(new ResultFormatOverlayProjection(
                        presentationValidator.restoreAndValidate(
                                rs.getString("payload"), rs.getString("summary_text")),
                        uuid(rs, "id"), rs.getInt("revision"))) : Optional.empty(),
                resultId, resultVersionId);
    }

    @Override
    public Optional<ManagementActivityResultDetail> findManagementDetail(UUID activityId, UUID schoolId) {
        return jdbc.query("""
                SELECT a.id AS activity_id, ar.id AS result_id,
                       ar.result_internal_status, ar.result_public_status,
                       ar.current_candidate_version_id, ar.current_internal_version_id,
                       ar.current_public_version_id, ar.public_visibility_blocked,
                       cv.id AS candidate_version_id, cv.version_number AS candidate_version_number,
                       cv.title AS candidate_title, cv.summary_text AS candidate_summary_text,
                       cv.score_highlights::text AS candidate_score_highlights,
                       cv.media_refs::text AS candidate_media_refs,
                       cv.published_internally_at AS candidate_published_internally_at,
                       cv.published_publicly_at AS candidate_published_publicly_at,
                       cvf.payload::text AS candidate_format_payload,
                       cvf.id AS candidate_format_record_id,
                       cvf.revision AS candidate_format_revision,
                       iv.id AS internal_version_id, iv.version_number AS internal_version_number,
                       iv.title AS internal_title, iv.summary_text AS internal_summary_text,
                       iv.score_highlights::text AS internal_score_highlights,
                       iv.media_refs::text AS internal_media_refs,
                       iv.published_internally_at AS internal_published_internally_at,
                       iv.published_publicly_at AS internal_published_publicly_at,
                       ivf.payload::text AS internal_format_payload,
                       ivf.id AS internal_format_record_id,
                       ivf.revision AS internal_format_revision,
                       pv.id AS public_version_id, pv.version_number AS public_version_number,
                       pv.title AS public_title, pv.summary_text AS public_summary_text,
                       pv.score_highlights::text AS public_score_highlights,
                       pv.media_refs::text AS public_media_refs,
                       pv.published_internally_at AS public_published_internally_at,
                       pv.published_publicly_at AS public_published_publicly_at,
                       pvf.payload::text AS public_format_payload,
                       pvf.id AS public_format_record_id,
                       pvf.revision AS public_format_revision
                FROM activities a
                LEFT JOIN activity_results ar
                  ON ar.activity_id = a.id
                 AND ar.school_id = a.school_id
                LEFT JOIN result_versions cv
                  ON cv.id = ar.current_candidate_version_id
                 AND cv.result_id = ar.id
                LEFT JOIN result_format_heads cvh ON cvh.result_version_id = cv.id AND cvh.result_id = ar.id
                LEFT JOIN result_format_edit_records cvf ON cvf.id = cvh.current_format_edit_record_id
                 AND cvf.result_id = ar.id AND cvf.result_version_id = cv.id
                LEFT JOIN result_versions iv
                  ON iv.id = ar.current_internal_version_id
                 AND iv.result_id = ar.id
                LEFT JOIN result_format_heads ivh ON ivh.result_version_id = iv.id AND ivh.result_id = ar.id
                LEFT JOIN result_format_edit_records ivf ON ivf.id = ivh.current_format_edit_record_id
                 AND ivf.result_id = ar.id AND ivf.result_version_id = iv.id
                LEFT JOIN result_versions pv
                  ON pv.id = ar.current_public_version_id
                 AND pv.result_id = ar.id
                LEFT JOIN result_format_heads pvh ON pvh.result_version_id = pv.id AND pvh.result_id = ar.id
                LEFT JOIN result_format_edit_records pvf ON pvf.id = pvh.current_format_edit_record_id
                 AND pvf.result_id = ar.id AND pvf.result_version_id = pv.id
                WHERE a.id = ? AND a.school_id = ?
                """, rs -> rs.next() ? Optional.of(mapManagementDetail(rs)) : Optional.empty(), activityId, schoolId);
    }

    @Override
    public QueryPage<ManagementActivityResultSummary> findManagementList(
            UUID schoolId, int page, int size, String internalStatus, String publicStatus, String query) {
        StringBuilder where = new StringBuilder(" WHERE ar.school_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        if (internalStatus != null) {
            where.append(" AND ar.result_internal_status = ?");
            args.add(internalStatus);
        }
        if (publicStatus != null) {
            where.append(" AND ar.result_public_status = ?");
            args.add(publicStatus);
        }
        if (query != null) {
            where.append(" AND LOWER(a.title) LIKE ?");
            args.add("%" + query + "%");
        }

        String select = """
                SELECT a.id AS activity_id, a.title AS activity_title, ar.id AS result_id,
                       ar.result_internal_status, ar.result_public_status,
                       ar.public_visibility_blocked, ar.current_candidate_version_id,
                       ar.current_internal_version_id, ar.current_public_version_id, ar.updated_at
                FROM activity_results ar
                JOIN activities a ON a.id = ar.activity_id AND a.school_id = ar.school_id
                """ + where + " ORDER BY ar.updated_at DESC, ar.id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<ManagementActivityResultSummary> items = jdbc.query(select, this::mapSummary, pageArgs.toArray());

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_results ar "
                        + "JOIN activities a ON a.id = ar.activity_id AND a.school_id = ar.school_id"
                        + where,
                Long.class,
                args.toArray());
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean existsActivity(UUID activityId, UUID schoolId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE id = ? AND school_id = ?",
                Long.class,
                activityId,
                schoolId);
        return count != null && count == 1;
    }

    @Override
    public List<ActivityResultHistoryEntry> findHistory(UUID activityId, UUID schoolId) {
        return jdbc.query("""
                SELECT rr.id, rr.action, rr.result_version_id,
                       COALESCE(rr.submitted_at, rr.reviewed_at, rr.created_at) AS occurred_at,
                       rr.reason, COALESCE(rr.submitted_by, rr.reviewer_id) AS actor_id
                FROM activities a
                JOIN activity_results ar
                  ON ar.activity_id = a.id
                 AND ar.school_id = a.school_id
                JOIN result_review_records rr ON rr.result_id = ar.id
                WHERE a.id = ? AND a.school_id = ?
                ORDER BY rr.created_at ASC, rr.id ASC
                """, (rs, row) -> new ActivityResultHistoryEntry(
                        uuid(rs, "id"), rs.getString("action"), uuid(rs, "result_version_id"),
                        instant(rs, "occurred_at"), rs.getString("reason"), uuid(rs, "actor_id")),
                activityId, schoolId);
    }

    private PublicActivityResultView mapPublic(ResultSet rs) throws SQLException {
        return new PublicActivityResultView(
                uuid(rs, "activity_id"), uuid(rs, "result_id"), uuid(rs, "version_id"),
                rs.getInt("version_number"), rs.getString("title"), rs.getString("summary_text"),
                readList(rs.getString("score_highlights"), STRING_LIST, "scoreHighlights"),
                instant(rs, "published_publicly_at"), restorePresentation(
                        rs.getString("format_payload"), rs.getString("summary_text")));
    }

    private ActivityResultStudentReadState mapStudentState(ResultSet rs) throws SQLException {
        return new ActivityResultStudentReadState(
                uuid(rs, "activity_id"), uuid(rs, "result_id"), rs.getString("execution_status"),
                rs.getString("result_internal_status"), rs.getString("result_public_status"),
                uuid(rs, "current_candidate_version_id"), uuid(rs, "current_internal_version_id"),
                uuid(rs, "current_public_version_id"), rs.getBoolean("public_visibility_blocked"),
                mapBaseVersion(rs, "internal", false), mapBaseVersion(rs, "public", false));
    }

    private ManagementActivityResultDetail mapManagementDetail(ResultSet rs) throws SQLException {
        UUID resultId = uuid(rs, "result_id");
        if (resultId == null) {
            return new ManagementActivityResultDetail(
                    uuid(rs, "activity_id"), null, "DRAFT", "NOT_SUBMITTED",
                    null, null, null, false, null, null, null);
        }
        return new ManagementActivityResultDetail(
                uuid(rs, "activity_id"), resultId,
                rs.getString("result_internal_status"), rs.getString("result_public_status"),
                uuid(rs, "current_candidate_version_id"), uuid(rs, "current_internal_version_id"),
                uuid(rs, "current_public_version_id"), rs.getBoolean("public_visibility_blocked"),
                mapVersion(rs, "candidate", true), mapVersion(rs, "internal", true),
                mapVersion(rs, "public", true));
    }

    private ManagementActivityResultSummary mapSummary(ResultSet rs, int row) throws SQLException {
        return new ManagementActivityResultSummary(
                uuid(rs, "activity_id"), rs.getString("activity_title"), uuid(rs, "result_id"),
                rs.getString("result_internal_status"), rs.getString("result_public_status"),
                rs.getBoolean("public_visibility_blocked"), uuid(rs, "current_candidate_version_id"),
                uuid(rs, "current_internal_version_id"), uuid(rs, "current_public_version_id"),
                instant(rs, "updated_at"));
    }

    private ActivityResultVersionProjection mapVersion(ResultSet rs, String prefix, boolean includeMedia)
            throws SQLException {
        UUID id = uuid(rs, prefix + "_version_id");
        if (id == null) return null;
        return new ActivityResultVersionProjection(
                id,
                rs.getInt(prefix + "_version_number"),
                rs.getString(prefix + "_title"),
                rs.getString(prefix + "_summary_text"),
                readList(rs.getString(prefix + "_score_highlights"), STRING_LIST, "scoreHighlights"),
                includeMedia
                        ? readList(rs.getString(prefix + "_media_refs"), UUID_LIST, "mediaRefs")
                        : List.of(),
                instant(rs, prefix + "_published_internally_at"),
                instant(rs, prefix + "_published_publicly_at"),
                restorePresentation(rs.getString(prefix + "_format_payload"),
                        rs.getString(prefix + "_summary_text")),
                uuid(rs, prefix + "_format_record_id"),
                integer(rs, prefix + "_format_revision"));
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type, String field) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.copyOf(objectMapper.readValue(json, type));
        } catch (JsonProcessingException ex) {
            throw new ActivityResultReadConsistencyException("Stored " + field + " are invalid");
        }
    }

    private ActivityResultVersionProjection mapBaseVersion(ResultSet rs, String prefix, boolean includeMedia)
            throws SQLException {
        UUID id = uuid(rs, prefix + "_version_id");
        if (id == null) return null;
        return new ActivityResultVersionProjection(
                id, rs.getInt(prefix + "_version_number"), rs.getString(prefix + "_title"),
                rs.getString(prefix + "_summary_text"),
                readList(rs.getString(prefix + "_score_highlights"), STRING_LIST, "scoreHighlights"),
                includeMedia ? readList(rs.getString(prefix + "_media_refs"), UUID_LIST, "mediaRefs") : List.of(),
                instant(rs, prefix + "_published_internally_at"),
                instant(rs, prefix + "_published_publicly_at"));
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
