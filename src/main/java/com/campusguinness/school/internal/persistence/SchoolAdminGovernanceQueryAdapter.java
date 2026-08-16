package com.campusguinness.school.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolAdminAccountResult;
import com.campusguinness.school.application.query.model.SchoolAdminInvitationQueryResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceDetailResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceListResult;
import com.campusguinness.school.application.query.port.SchoolAdminGovernanceQueryPort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolAdminGovernanceQueryAdapter implements SchoolAdminGovernanceQueryPort {

    private static final String ACTIVE_ADMIN_COUNT = """
            (select count(*)
               from school_memberships m
               join users u on u.id = m.user_id
              where m.school_id = s.id
                and m.role_in_school = 'SCHOOL_ADMIN'
                and m.status = 'ACTIVE'
                and u.account_status = 'NORMAL')
            """;

    private static final RowMapper<SchoolGovernanceListResult> SCHOOL_LIST_MAPPER = (rs, rowNum) ->
            new SchoolGovernanceListResult(
                    uuid(rs, "id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getString("school_type"),
                    rs.getString("region"),
                    rs.getString("internal_code"),
                    rs.getString("unified_code_type"),
                    rs.getString("unified_code"),
                    rs.getLong("normal_active_school_admin_count")
            );

    private static final RowMapper<SchoolGovernanceDetailResult> SCHOOL_DETAIL_MAPPER = (rs, rowNum) ->
            new SchoolGovernanceDetailResult(
                    uuid(rs, "id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getString("internal_code"),
                    rs.getString("unified_code_type"),
                    rs.getString("unified_code"),
                    rs.getString("school_type"),
                    rs.getString("region"),
                    rs.getString("address"),
                    rs.getString("contact_name"),
                    rs.getString("contact_phone"),
                    rs.getString("contact_email"),
                    rs.getLong("normal_active_school_admin_count"),
                    instant(rs, "created_at"),
                    instant(rs, "updated_at")
            );

    private static final RowMapper<SchoolAdminAccountResult> SCHOOL_ADMIN_MAPPER = (rs, rowNum) ->
            new SchoolAdminAccountResult(
                    uuid(rs, "user_id"),
                    rs.getString("username"),
                    rs.getString("account_status"),
                    rs.getString("membership_status"),
                    instant(rs, "started_at"),
                    instant(rs, "locked_until")
            );

    private static final RowMapper<SchoolAdminInvitationQueryResult> INVITATION_MAPPER = (rs, rowNum) ->
            new SchoolAdminInvitationQueryResult(
                    uuid(rs, "invitation_id"),
                    uuid(rs, "user_id"),
                    rs.getString("username"),
                    uuid(rs, "school_id"),
                    rs.getString("status"),
                    instant(rs, "expires_at"),
                    instant(rs, "accepted_at"),
                    instant(rs, "revoked_at"),
                    instant(rs, "created_at"),
                    rs.getBoolean("expired")
            );

    private final NamedParameterJdbcTemplate jdbc;

    SchoolAdminGovernanceQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<SchoolGovernanceListResult> findSchools(
            String status,
            String search,
            int page,
            int size
    ) {
        var params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        var where = new StringBuilder(" where 1 = 1 ");
        if (status != null) {
            where.append("and s.school_status = :status ");
            params.addValue("status", status);
        }
        if (search != null) {
            where.append("""
                    and (lower(s.name) like :search escape '\\'
                         or lower(s.internal_code) like :search escape '\\'
                         or lower(coalesce(s.unified_code, '')) like :search escape '\\')
                    """);
            params.addValue("search", "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%");
        }
        String select = """
                select s.id,
                       s.name,
                       s.school_status as status,
                       s.school_type,
                       s.region,
                       s.internal_code,
                       s.unified_code_type,
                       s.unified_code,
                """ + ACTIVE_ADMIN_COUNT + " as normal_active_school_admin_count " +
                "from schools s " + where +
                " order by s.name asc, s.id asc limit :limit offset :offset";
        List<SchoolGovernanceListResult> items = jdbc.query(select, params, SCHOOL_LIST_MAPPER);
        Long total = jdbc.queryForObject("select count(*) from schools s " + where, params, Long.class);
        return new QueryPage<>(items, page, size, total != null ? total : 0);
    }

    @Override
    public Optional<SchoolGovernanceDetailResult> findSchool(UUID schoolId) {
        String sql = """
                select s.id,
                       s.name,
                       s.school_status as status,
                       s.internal_code,
                       s.unified_code_type,
                       s.unified_code,
                       s.school_type,
                       s.region,
                       s.address,
                       s.contact_name,
                       s.contact_phone,
                       s.contact_email,
                """ + ACTIVE_ADMIN_COUNT + " as normal_active_school_admin_count, " +
                "s.created_at, s.updated_at from schools s where s.id = :schoolId";
        return jdbc.query(sql, new MapSqlParameterSource("schoolId", schoolId), SCHOOL_DETAIL_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public List<SchoolAdminAccountResult> findSchoolAdmins(UUID schoolId) {
        String sql = """
                select u.id as user_id,
                       u.username,
                       u.account_status,
                       m.status as membership_status,
                       m.started_at,
                       u.locked_until
                  from school_memberships m
                  join users u on u.id = m.user_id
                 where m.school_id = :schoolId
                   and m.role_in_school = 'SCHOOL_ADMIN'
                 order by u.username asc, m.started_at asc, m.id asc
                """;
        return jdbc.query(sql, new MapSqlParameterSource("schoolId", schoolId), SCHOOL_ADMIN_MAPPER);
    }

    @Override
    public QueryPage<SchoolAdminInvitationQueryResult> findInvitations(
            UUID schoolId,
            String status,
            int page,
            int size
    ) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = """
                where i.school_id = :schoolId
                  and i.role_in_school = 'SCHOOL_ADMIN'
                """;
        if (status != null) {
            where += " and i.invitation_status = :status ";
            params.addValue("status", status);
        }
        String sql = invitationSelect() + where +
                " order by i.created_at desc, i.id desc limit :limit offset :offset";
        List<SchoolAdminInvitationQueryResult> items = jdbc.query(sql, params, INVITATION_MAPPER);
        Long total = jdbc.queryForObject(
                "select count(*) from school_admin_invitations i " + where,
                params,
                Long.class
        );
        return new QueryPage<>(items, page, size, total != null ? total : 0);
    }

    @Override
    public Optional<SchoolAdminInvitationQueryResult> findInvitation(UUID schoolId, UUID invitationId) {
        String sql = invitationSelect() + """
                where i.school_id = :schoolId
                  and i.id = :invitationId
                  and i.role_in_school = 'SCHOOL_ADMIN'
                """;
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("invitationId", invitationId);
        return jdbc.query(sql, params, INVITATION_MAPPER).stream().findFirst();
    }

    private String invitationSelect() {
        return """
                select i.id as invitation_id,
                       i.user_id,
                       u.username,
                       i.school_id,
                       i.invitation_status as status,
                       i.expires_at,
                       i.accepted_at,
                       i.revoked_at,
                       i.created_at,
                       (i.invitation_status = 'PENDING' and i.expires_at <= current_timestamp) as expired
                  from school_admin_invitations i
                  join users u on u.id = i.user_id
                """;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
