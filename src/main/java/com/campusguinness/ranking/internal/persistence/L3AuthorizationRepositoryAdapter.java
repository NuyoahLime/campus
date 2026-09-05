package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.internal.domain.AuthorizationStatus;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class L3AuthorizationRepositoryAdapter implements L3AuthorizationRepository {
    private final L3AuthorizationJpaRepository jpa;
    private final JdbcTemplate jdbc;
    L3AuthorizationRepositoryAdapter(L3AuthorizationJpaRepository r, JdbcTemplate jdbc) {
        this.jpa = r;
        this.jdbc = jdbc;
    }
    @Override
    @Transactional
    public void save(L3Authorization a) {
        var entity = jpa.findById(a.id().value())
                .orElseGet(() -> L3AuthorizationPersistenceMapper.toEntity(a));
        L3AuthorizationPersistenceMapper.copyToEntity(a, entity);
        jpa.save(entity);
    }
    @Override @Transactional(readOnly = true) public Optional<L3Authorization> findById(L3AuthorizationId id) {
        return jpa.findById(id.value()).map(L3AuthorizationPersistenceMapper::toDomain);
    }
    @Override @Transactional public Optional<L3Authorization> findByIdForUpdate(L3AuthorizationId id) {
        return jpa.findByIdForUpdate(id.value()).map(L3AuthorizationPersistenceMapper::toDomain);
    }
    @Override @Transactional public List<L3Authorization> findBySchoolIdAndStatusesForUpdate(
            UUID schoolId, Collection<AuthorizationStatus> statuses) {
        return jpa.findBySchoolIdAndAuthorizationStatusInForUpdate(
                        schoolId,
                        statuses.stream().map(AuthorizationStatus::name).toList())
                .stream()
                .map(L3AuthorizationPersistenceMapper::toDomain)
                .toList();
    }
    @Override @Transactional(readOnly = true) public boolean existsNonWithdrawnByBusinessKey(
            UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM l3_authorizations
                WHERE school_id = ?
                  AND project_id = ?
                  AND rule_version_id = ?
                  AND COALESCE(data_scope, '{}'::jsonb) = ?::jsonb
                  AND authorization_status <> 'WITHDRAWN'
                """, Integer.class, schoolId, projectId, ruleVersionId, dataScope);
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsNonWithdrawnByBusinessKeyExcluding(
            UUID schoolId, UUID projectId, UUID ruleVersionId, String dataScope, UUID excludedId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM l3_authorizations
                WHERE school_id = ?
                  AND project_id = ?
                  AND rule_version_id = ?
                  AND COALESCE(data_scope, '{}'::jsonb) = ?::jsonb
                  AND authorization_status <> 'WITHDRAWN'
                  AND id <> ?
                """, Integer.class, schoolId, projectId, ruleVersionId, dataScope, excludedId);
        return count != null && count > 0;
    }
}
