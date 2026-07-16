package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class ScoreAttemptRepositoryAdapter implements ScoreAttemptRepository {
    private final ScoreAttemptJpaRepository jpaRepository;
    ScoreAttemptRepositoryAdapter(ScoreAttemptJpaRepository r) { this.jpaRepository = r; }
    @Override @Transactional public void save(ScoreAttempt s) { jpaRepository.save(ScoreAttemptPersistenceMapper.toEntity(s)); }
    @Override @Transactional(readOnly = true) public Optional<ScoreAttempt> findById(ScoreAttemptId id) {
        return jpaRepository.findById(id.value()).map(ScoreAttemptPersistenceMapper::toDomain);
    }
}
