package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.ScoreAppeal;
import com.campusguinness.appeal.internal.domain.ScoreAppealId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ScoreAppealRepositoryAdapter implements ScoreAppealRepository {
    private final ScoreAppealJpaRepository jpa;
    ScoreAppealRepositoryAdapter(ScoreAppealJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(ScoreAppeal a) {
        var existing = jpa.findById(a.id().value());
        if (existing.isPresent()) {
            ScoreAppealPersistenceMapper.updateEntity(existing.get(), a);
            jpa.saveAndFlush(existing.get());
        } else {
            jpa.saveAndFlush(ScoreAppealPersistenceMapper.toEntity(a));
        }
    }
    @Override @Transactional(readOnly = true) public Optional<ScoreAppeal> findById(ScoreAppealId id) {
        return jpa.findById(id.value()).map(ScoreAppealPersistenceMapper::toDomain);
    }
    @Override @Transactional(readOnly = true)
    public List<ScoreAppeal> findByStudentId(UUID studentId) {
        return jpa.findByStudentId(studentId).stream()
                .map(ScoreAppealPersistenceMapper::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ScoreAppeal> findByIdAndStudentId(UUID id, UUID studentId) {
        return jpa.findByIdAndStudentId(id, studentId)
                .map(ScoreAppealPersistenceMapper::toDomain);
    }
}
