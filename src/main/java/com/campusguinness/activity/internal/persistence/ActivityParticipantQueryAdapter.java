package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

@Component
@Transactional(readOnly = true)
class ActivityParticipantQueryAdapter implements ActivityParticipantQueryPort {

    private final ActivityParticipantJpaRepository participantJpa;
    private final JdbcTemplate jdbc;

    ActivityParticipantQueryAdapter(ActivityParticipantJpaRepository participantJpa,
                                     JdbcTemplate jdbc) {
        this.participantJpa = participantJpa;
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<ParticipantListResult> findByActivity(UUID activityId, String keyword, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String keywordPattern = normalizedKeyword.isEmpty()
                ? null
                : "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";

        String filterSql = """
                FROM activity_participants ap
                JOIN school_memberships sm
                  ON sm.id = ap.student_membership_id
                JOIN users u
                  ON u.id = sm.user_id
                WHERE ap.activity_id = ?
                  AND sm.role_in_school = 'STUDENT'
                  AND sm.status = 'ACTIVE'
                """ + (keywordPattern == null ? "" : " AND LOWER(u.username) LIKE ?\n");

        List<Object> filterArgs = new ArrayList<>();
        filterArgs.add(activityId);
        if (keywordPattern != null) {
            filterArgs.add(keywordPattern);
        }

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + filterSql,
                Long.class,
                filterArgs.toArray());

        String dataSql = """
                SELECT ap.id AS participant_id,
                       ap.activity_id,
                       ap.student_membership_id,
                       sm.user_id AS student_id,
                       u.username AS display_name,
                       sp.grade,
                       sp.class_name,
                       sp.student_number,
                       COUNT(DISTINCT app.id) AS assigned_project_count,
                       EXISTS (
                           SELECT 1
                           FROM score_attempts sa
                           JOIN activity_projects score_ap
                             ON score_ap.id = sa.activity_project_id
                           WHERE score_ap.activity_id = ap.activity_id
                             AND sa.student_id = sm.user_id
                       ) AS has_score_attempt,
                       ap.created_at AS joined_at
                FROM activity_participants ap
                JOIN school_memberships sm
                  ON sm.id = ap.student_membership_id
                JOIN users u
                  ON u.id = sm.user_id
                LEFT JOIN student_profiles sp
                  ON sp.membership_id = sm.id
                LEFT JOIN activity_project_participants app
                  ON app.activity_participant_id = ap.id
                WHERE ap.activity_id = ?
                  AND sm.role_in_school = 'STUDENT'
                  AND sm.status = 'ACTIVE'
                """ + (keywordPattern == null ? "" : " AND LOWER(u.username) LIKE ?\n") + """
                GROUP BY ap.id,
                         ap.activity_id,
                         ap.student_membership_id,
                         sm.user_id,
                         u.username,
                         sp.grade,
                         sp.class_name,
                         sp.student_number,
                         ap.created_at
                ORDER BY ap.created_at DESC, ap.id DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> dataArgs = new ArrayList<>(filterArgs);
        dataArgs.add(size);
        dataArgs.add(page * size);

        var items = jdbc.query(dataSql, (rs, rowNum) -> {
            Timestamp joinedAt = rs.getTimestamp("joined_at");
            return new ParticipantListResult(
                    rs.getObject("participant_id", UUID.class),
                    rs.getObject("activity_id", UUID.class),
                    rs.getObject("student_membership_id", UUID.class),
                    rs.getObject("student_id", UUID.class),
                    rs.getString("display_name"),
                    rs.getString("grade"),
                    rs.getString("class_name"),
                    rs.getString("student_number"),
                    rs.getLong("assigned_project_count"),
                    rs.getBoolean("has_score_attempt"),
                    joinedAt.toInstant());
        }, dataArgs.toArray());

        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public List<UUID> findParticipantIdsByMembership(UUID studentMembershipId) {
        return participantJpa.findByStudentMembershipId(studentMembershipId)
                .stream().map(ActivityParticipantEntity::getId).toList();
    }

    @Override
    public List<UUID> findParticipantIdsByMembershipIds(List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return List.of();
        return participantJpa.findByStudentMembershipIdIn(membershipIds)
                .stream().map(ActivityParticipantEntity::getId).toList();
    }

    @Override
    public Optional<ParticipantListResult> findByActivityAndMemberships(UUID activityId, List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return Optional.empty();
        var entities = participantJpa.findByStudentMembershipIdIn(membershipIds);
        return entities.stream()
                .filter(e -> e.getActivityId().equals(activityId))
                .findFirst()
                .map(e -> new ParticipantListResult(e.getId(), e.getActivityId(), e.getStudentMembershipId(),
                        null, null, null, null, null, 0, false, e.getCreatedAt()));
    }
}
