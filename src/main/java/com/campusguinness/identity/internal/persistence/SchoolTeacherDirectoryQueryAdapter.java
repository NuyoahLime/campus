package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.port.SchoolTeacherDirectoryQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolTeacherDirectoryQueryAdapter implements SchoolTeacherDirectoryQueryPort {

    private final JdbcTemplate jdbc;

    SchoolTeacherDirectoryQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public QueryPage<SchoolTeacherItem> findActiveTeachers(UUID schoolId, String keyword, int page, int size) {
        String kw = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        String countSql = "SELECT COUNT(*) FROM school_memberships sm "
                + "JOIN users u ON sm.user_id = u.id "
                + "LEFT JOIN teacher_profiles tp ON u.id = tp.user_id "
                + "WHERE sm.school_id = ? AND sm.role_in_school = 'TEACHER' AND sm.status = 'ACTIVE' AND u.account_status = 'NORMAL'"
                + (kw != null ? " AND (LOWER(u.username) LIKE ? OR LOWER(COALESCE(tp.subject,'')) LIKE ? OR LOWER(COALESCE(tp.title,'')) LIKE ?)" : "");

        List<Object> countArgs = new java.util.ArrayList<>(List.of(schoolId));
        if (kw != null) countArgs.addAll(List.of(kw, kw, kw));
        long total = jdbc.queryForObject(countSql, Long.class, countArgs.toArray());

        String dataSql = "SELECT u.id AS user_id, sm.id AS membership_id, u.username, "
                + "COALESCE(tp.subject,'') AS subject, COALESCE(tp.title,'') AS title "
                + "FROM school_memberships sm "
                + "JOIN users u ON sm.user_id = u.id "
                + "LEFT JOIN teacher_profiles tp ON u.id = tp.user_id "
                + "WHERE sm.school_id = ? AND sm.role_in_school = 'TEACHER' AND sm.status = 'ACTIVE' AND u.account_status = 'NORMAL'"
                + (kw != null ? " AND (LOWER(u.username) LIKE ? OR LOWER(COALESCE(tp.subject,'')) LIKE ? OR LOWER(COALESCE(tp.title,'')) LIKE ?)" : "")
                + " ORDER BY u.username ASC, u.id ASC LIMIT ? OFFSET ?";

        List<Object> dataArgs = new java.util.ArrayList<>(List.of(schoolId));
        if (kw != null) dataArgs.addAll(List.of(kw, kw, kw));
        dataArgs.add(size);
        dataArgs.add(page * size);

        var items = jdbc.query(dataSql, (rs, rowNum) -> new SchoolTeacherItem(
                rs.getObject("user_id", UUID.class),
                rs.getObject("membership_id", UUID.class),
                rs.getString("username"),
                rs.getString("subject"),
                rs.getString("title")), dataArgs.toArray());

        return new QueryPage<>(items, page, size, total);
    }
}
