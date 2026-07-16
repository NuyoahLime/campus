package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class L3AuthorizationRepositoryAdapter implements L3AuthorizationRepository {
    private final L3AuthorizationJpaRepository jpa;
    L3AuthorizationRepositoryAdapter(L3AuthorizationJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(L3Authorization a) { jpa.save(L3AuthorizationPersistenceMapper.toEntity(a)); }
    @Override @Transactional(readOnly = true) public Optional<L3Authorization> findById(L3AuthorizationId id) {
        return jpa.findById(id.value()).map(L3AuthorizationPersistenceMapper::toDomain);
    }
}
