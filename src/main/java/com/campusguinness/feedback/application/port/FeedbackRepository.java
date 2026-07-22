package com.campusguinness.feedback.application.port;
import com.campusguinness.feedback.internal.domain.Feedback;
import com.campusguinness.feedback.internal.domain.FeedbackId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface FeedbackRepository { void save(Feedback f); Optional<Feedback> findById(FeedbackId id); List<Feedback> findBySchoolId(UUID schoolId); List<Feedback> findBySubmitterId(UUID submitterId); Optional<Feedback> findByIdAndSubmitterId(UUID id, UUID submitterId); }
