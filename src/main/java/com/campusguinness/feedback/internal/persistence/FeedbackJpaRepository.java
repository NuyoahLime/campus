package com.campusguinness.feedback.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface FeedbackJpaRepository extends JpaRepository<FeedbackEntity, UUID> {
    List<FeedbackEntity> findBySchoolId(UUID schoolId);
}
