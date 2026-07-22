package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FeedbackApplicationService {
    private final FeedbackRepository repo;
    public FeedbackApplicationService(FeedbackRepository r) { this.repo = r; }

    public FeedbackResult submit(UUID schoolId, UUID submitterId, String feedbackType, String content) {
        var f = Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID()))
                .schoolId(schoolId).submitterId(submitterId).feedbackType(feedbackType).content(content));
        repo.save(f);
        return new FeedbackResult(f.id().value(), f.status().name());
    }

    @Transactional(readOnly = true)
    public List<FeedbackResult> listBySchool(UUID schoolId) {
        return repo.findBySchoolId(schoolId).stream()
                .map(f -> new FeedbackResult(f.id().value(), f.status().name())).toList();
    }

    public Feedback beginProcessing(UUID id, UUID handlerId) { var f=find(id); f.beginProcessing(handlerId); repo.save(f); return f; }
    public FeedbackResult resolve(UUID id, String reply) { var f=find(id); f.resolve(reply); repo.save(f); return result(f); }
    public FeedbackResult close(UUID id, String reason) { var f=find(id); f.close(reason); repo.save(f); return result(f); }

    @Transactional(readOnly = true)
    public List<FeedbackResult> listMine(UUID submitterId) {
        return repo.findBySubmitterId(submitterId).stream()
                .map(f -> new FeedbackResult(f.id().value(), f.status().name())).toList();
    }

    @Transactional(readOnly = true)
    public FeedbackResult getMine(UUID id, UUID submitterId) {
        return repo.findByIdAndSubmitterId(id, submitterId)
                .map(f -> new FeedbackResult(f.id().value(), f.status().name()))
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + id));
    }

    private Feedback find(UUID id) { return repo.findById(new FeedbackId(id)).orElseThrow(()->new IllegalArgumentException("Feedback not found: "+id)); }
    private FeedbackResult result(Feedback f) { return new FeedbackResult(f.id().value(), f.status().name()); }
}
