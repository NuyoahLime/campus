package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class ScoreAttemptRepositoryAdapter implements ScoreAttemptRepository {
    private final ScoreAttemptJpaRepository jpaRepository;
    ScoreAttemptRepositoryAdapter(ScoreAttemptJpaRepository r) { this.jpaRepository = r; }
    @Override @Transactional public void save(ScoreAttempt s) {
        try {
            var existing = jpaRepository.findById(s.id().value());
            if (existing.isPresent()) {
                ScoreAttemptPersistenceMapper.updateEntity(existing.get(), s);
                jpaRepository.saveAndFlush(existing.get());
            } else {
                jpaRepository.saveAndFlush(ScoreAttemptPersistenceMapper.toEntity(s));
            }
        } catch (DataIntegrityViolationException ex) {
            if (hasConstraint(ex, "uq_score_attempt_ap_student_num")) {
                throw new ScoreWriteException("SCORE_ATTEMPT_CONFLICT",
                        "Another score attempt was created at the same time.");
            }
            throw ex;
        }
    }
    @Override @Transactional(readOnly = true) public Optional<ScoreAttempt> findById(ScoreAttemptId id) {
        return jpaRepository.findById(id.value()).map(ScoreAttemptPersistenceMapper::toDomain);
    }

    private boolean hasConstraint(Throwable error, String expected) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql
                    && "23505".equals(sql.getSQLState())
                    && current.getMessage() != null
                    && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
