package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.Feedback;
import com.campusguinness.feedback.internal.domain.FeedbackId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class FeedbackRepositoryAdapter implements FeedbackRepository {
    private final FeedbackJpaRepository jpa;
    FeedbackRepositoryAdapter(FeedbackJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(Feedback f) { jpa.save(FeedbackPersistenceMapper.toEntity(f)); }
    @Override @Transactional(readOnly = true) public Optional<Feedback> findById(FeedbackId id) {
        return jpa.findById(id.value()).map(FeedbackPersistenceMapper::toDomain);
    }
    @Override @Transactional(readOnly = true)
    public List<Feedback> findBySchoolId(UUID schoolId) {
        return jpa.findBySchoolId(schoolId).stream().map(FeedbackPersistenceMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<Feedback> findBySubmitterId(UUID submitterId) {
        return jpa.findBySubmitterId(submitterId).stream().map(FeedbackPersistenceMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true)
    public Optional<Feedback> findByIdAndSubmitterId(UUID id, UUID submitterId) {
        return jpa.findByIdAndSubmitterId(id, submitterId).map(FeedbackPersistenceMapper::toDomain);
    }
    @Override @Transactional(readOnly = true)
    public Optional<Feedback> findByIdAndSchoolId(UUID id, UUID schoolId) {
        return jpa.findByIdAndSchoolId(id, schoolId).map(FeedbackPersistenceMapper::toDomain);
    }
}
