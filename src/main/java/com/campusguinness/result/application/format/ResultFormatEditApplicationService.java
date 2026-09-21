package com.campusguinness.result.application.format;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ResultFormatEditApplicationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SchoolResourceAuthorization authorization;
    private final CurrentActor actor;

    public ResultFormatEditApplicationService(JdbcTemplate jdbc, ObjectMapper mapper,
            SchoolResourceAuthorization authorization, CurrentActor actor) {
        this.jdbc = jdbc; this.mapper = mapper; this.authorization = authorization; this.actor = actor;
    }

    public ResultFormatEditResult append(UUID resultId, UUID versionId, String reason, JsonNode presentation) {
        if (resultId == null || versionId == null) throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
        Boolean lockAcquired = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?::text, 0))", Boolean.class, versionId);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            throw error("ACTIVITY_RESULT_FORMAT_CONFLICT", "Concurrent format edit conflict");
        }
        var target = jdbc.query("""
                SELECT ar.id AS result_id, ar.school_id, ar.current_candidate_version_id, ar.current_internal_version_id,
                       ar.current_public_version_id, ar.result_internal_status, ar.result_public_status,
                       ar.public_visibility_blocked, a.execution_status
                FROM activity_results ar JOIN activities a ON a.id = ar.activity_id
                WHERE ar.id = ? AND a.school_id = ar.school_id
                """, rs -> rs.next() ? new Target(rs.getObject("result_id", UUID.class), rs.getObject("school_id", UUID.class),
                rs.getObject("current_candidate_version_id", UUID.class),
                rs.getObject("current_internal_version_id", UUID.class),
                rs.getObject("current_public_version_id", UUID.class), rs.getString("result_internal_status"),
                rs.getString("result_public_status"), rs.getBoolean("public_visibility_blocked"),
                rs.getString("execution_status")) : null, resultId);
        if (target == null) throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
        try {
            authorization.requireSchoolAdmin(target.schoolId());
        } catch (IdentityApplicationException ex) {
            throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
        }
        actor.requireUserId();
        validateTarget(target, versionId);
        String cleanReason = validateReason(reason);
        JsonNode canonical = validatePresentation(presentation, exactSummary(resultId, versionId));
        UUID editor = actor.requireUserId();
        int expected = jdbc.query("SELECT version FROM result_format_heads WHERE result_id = ? AND result_version_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, resultId, versionId);
        int revision = expected + 1;
        UUID record = UUID.randomUUID();
        String payload = canonical.toString();
        jdbc.update("INSERT INTO result_format_edit_records(id,result_id,result_version_id,revision,payload,reason,edited_by,edited_at) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, now())",
                record, resultId, versionId, revision, payload, cleanReason, editor);
        if (expected == 0) {
            jdbc.update("INSERT INTO result_format_heads(result_version_id,result_id,current_format_edit_record_id,version,updated_at) VALUES (?, ?, ?, 1, now())",
                    versionId, resultId, record);
        } else {
            int changed = jdbc.update("UPDATE result_format_heads SET current_format_edit_record_id=?, version=version+1, updated_at=now() WHERE result_version_id=? AND result_id=? AND version=?",
                    record, versionId, resultId, expected);
            if (changed != 1) throw error("ACTIVITY_RESULT_FORMAT_CONFLICT", "Concurrent format edit conflict");
        }
        return new ResultFormatEditResult(record, resultId, versionId, revision,
                mapper.convertValue(canonical, ResultFormatPresentation.class), cleanReason, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ResultFormatHistoryEntry> history(UUID resultId, UUID versionId) {
        actor.requireUserId();
        var target = jdbc.query("SELECT ar.school_id FROM activity_results ar JOIN result_versions v ON v.result_id=ar.id AND v.id=? WHERE ar.id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, versionId, resultId);
        if (target == null) throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
        try {
            authorization.requireSchoolAdmin(target);
        } catch (IdentityApplicationException ex) {
            throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
        }
        return jdbc.query("SELECT id, revision, payload, reason, edited_by, edited_at FROM result_format_edit_records WHERE result_id=? AND result_version_id=? ORDER BY revision ASC",
                (rs, n) -> new ResultFormatHistoryEntry(rs.getObject("id", UUID.class), resultId, versionId,
                        rs.getInt("revision"), parsePresentation(rs.getString("payload")), rs.getString("reason"),
                        rs.getObject("edited_by", UUID.class),
                        rs.getTimestamp("edited_at").toInstant()), resultId, versionId);
    }

    private String exactSummary(UUID resultId, UUID versionId) {
        return jdbc.query("SELECT summary_text FROM result_versions WHERE id=? AND result_id=?",
                rs -> rs.next() ? rs.getString(1) : null, versionId, resultId);
    }
    private void validateTarget(Target t, UUID versionId) {
        if (!Set.of("PUBLISHED", "IN_PROGRESS", "ENDED").contains(t.executionStatus())) conflict();
        if (Set.of("ANOMALY_PENDING", "PLATFORM_TAKEDOWN").contains(t.publicStatus())) conflict();
        boolean candidateOnly = versionId.equals(t.candidate()) && !versionId.equals(t.internal()) && !versionId.equals(t.publicVersion());
        if (candidateOnly) conflict();
        boolean reviewBound = versionId.equals(t.candidate()) && Set.of("PENDING_PUBLIC_REVIEW", "PLATFORM_APPROVED", "PLATFORM_REJECTED").contains(t.publicStatus());
        if (reviewBound) conflict();
        boolean internal = versionId.equals(t.internal()) && "INTERNAL_PUBLISHED".equals(t.internalStatus());
        boolean publicVersion = versionId.equals(t.publicVersion()) && !t.blocked();
        if (!internal && !publicVersion) conflict();
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM result_versions WHERE id=? AND result_id=?)", Boolean.class, versionId, t.resultId());
        if (!Boolean.TRUE.equals(exists)) throw error("ACTIVITY_RESULT_FORMAT_NOT_FOUND", "Result format target not found");
    }
    private JsonNode validatePresentation(JsonNode input, String summary) {
        if (input == null || !input.isObject()) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "presentation must be an object");
        Set<String> keys = Set.of("paragraphs", "emphasisRanges"); input.fieldNames().forEachRemaining(k -> { if (!keys.contains(k)) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "unknown presentation key"); });
        JsonNode ps = input.get("paragraphs"), es = input.get("emphasisRanges");
        if (ps == null || ps.isNull()) ps = mapper.createArrayNode(); if (es == null || es.isNull()) es = mapper.createArrayNode();
        if (!ps.isArray() || !es.isArray() || ps.size() > 200 || es.size() > 200) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "invalid presentation collections");
        int length = summary == null ? -1 : summary.codePointCount(0, summary.length());
        int previous = 0;
        for (JsonNode p : ps) { requireKeys(p, Set.of("start","end","style")); requireIntegerRange(p); int s=p.path("start").intValue(), e=p.path("end").intValue(); if (s != previous || e <= s || e > length || !p.path("style").isTextual() || !"NORMAL".equals(p.path("style").textValue())) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "invalid paragraph range"); previous=e; }
        if (summary != null && previous != length) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "paragraphs must cover summaryText");
        List<ResultFormatPresentation.EmphasisRange> ranges = new ArrayList<>();
        for (JsonNode e : es) { requireKeys(e, Set.of("start","end","style")); requireIntegerRange(e); int s=e.path("start").intValue(), end=e.path("end").intValue(); String style=e.path("style").isTextual()?e.path("style").textValue():""; if (s<0||end<=s||end>length||!Set.of("BOLD","ITALIC").contains(style)) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "invalid emphasis range"); boolean contained=false; for(JsonNode p:ps) if(s>=p.path("start").intValue()&&end<=p.path("end").intValue()) contained=true; if(!contained) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "emphasis crosses paragraph"); for(var r:ranges) if((s==r.start()&&end==r.end())||(r.style().equals(style)&&s<r.end()&&r.start()<end)) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "overlapping emphasis"); ranges.add(new ResultFormatPresentation.EmphasisRange(s,end,style)); }
        ranges.sort(Comparator.comparingInt(ResultFormatPresentation.EmphasisRange::start)
                .thenComparingInt(ResultFormatPresentation.EmphasisRange::end)
                .thenComparing(ResultFormatPresentation.EmphasisRange::style));
        return mapper.valueToTree(new ResultFormatPresentation(mapper.convertValue(ps, mapper.getTypeFactory().constructCollectionType(List.class, ResultFormatPresentation.Paragraph.class)), ranges));
    }
    private void requireKeys(JsonNode n, Set<String> allowed) { if(!n.isObject()) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "invalid presentation item"); n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k)) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "unknown presentation item key");}); }
    private void requireIntegerRange(JsonNode node) { if (!node.path("start").isIntegralNumber() || !node.path("end").isIntegralNumber()) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "range bounds must be integers"); }
    private ResultFormatPresentation parsePresentation(String value) { try { return mapper.readValue(value, ResultFormatPresentation.class); } catch(Exception e) { throw error("ACTIVITY_RESULT_DATA_CONSISTENCY", "Stored format overlay is invalid"); } }
    private String validateReason(String reason) { String v=reason==null?null:reason.trim(); if(v==null||v.isBlank()||v.length()>2000) throw error("ACTIVITY_RESULT_FORMAT_INVALID", "reason must be non-blank and at most 2000 characters"); return v; }
    private void conflict(){throw error("ACTIVITY_RESULT_FORMAT_TARGET_CONFLICT", "Format edit target is not eligible");}
    private ResultFormatEditException error(String c,String m){return new ResultFormatEditException(c,m);}
    private record Target(UUID resultId, UUID schoolId, UUID candidate, UUID internal, UUID publicVersion, String internalStatus, String publicStatus, boolean blocked, String executionStatus) {}
}
