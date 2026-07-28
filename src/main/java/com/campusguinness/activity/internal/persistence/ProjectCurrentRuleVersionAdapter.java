package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ProjectCurrentRuleVersionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ProjectCurrentRuleVersionAdapter implements ProjectCurrentRuleVersionPort {

    private final JdbcTemplate jdbc;

    ProjectCurrentRuleVersionAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<UUID> findCurrentRuleVersionId(UUID projectId) {
        var list = jdbc.query(
                "SELECT current_rule_version_id FROM challenge_projects WHERE id = ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                projectId);
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }
}
