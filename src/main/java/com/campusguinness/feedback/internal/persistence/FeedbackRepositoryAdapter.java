package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.Feedback;
import com.campusguinness.feedback.internal.domain.FeedbackId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class FeedbackRepositoryAdapter implements FeedbackRepository {
    private final FeedbackJpaRepository jpa;
    FeedbackRepositoryAdapter(FeedbackJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(Feedback f) { jpa.save(FeedbackPersistenceMapper.toEntity(f)); }
    @Override @Transactional(readOnly = true) public Optional<Feedback> findById(FeedbackId id) {
        return jpa.findById(id.value()).map(FeedbackPersistenceMapper::toDomain);
    }
}
