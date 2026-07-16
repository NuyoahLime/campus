package com.campusguinness.feedback.application.port;
import com.campusguinness.feedback.internal.domain.Feedback;
import com.campusguinness.feedback.internal.domain.FeedbackId;
import java.util.Optional;
public interface FeedbackRepository { void save(Feedback f); Optional<Feedback> findById(FeedbackId id); }
