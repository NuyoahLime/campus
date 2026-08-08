package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class FeedbackApplicationService {
    private final FeedbackRepository repo;
    private final CurrentActor currentActor;

    public FeedbackApplicationService(FeedbackRepository r, CurrentActor currentActor) {
        this.repo = r;
        this.currentActor = currentActor;
    }

    public FeedbackResult submit(UUID schoolId, String feedbackType, String content) {
        UUID actorUserId = currentActor.requireUserId();
        var f = Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID()))
                .schoolId(schoolId).submitterId(actorUserId).feedbackType(feedbackType).content(content));
        repo.save(f);
        return new FeedbackResult(f.id().value(), f.status().name());
    }
    public FeedbackResult beginProcessing(UUID id, UUID handlerId) { var f=find(id); f.beginProcessing(handlerId); repo.save(f); return result(f); }
    public FeedbackResult resolve(UUID id, String reply) { var f=find(id); f.resolve(reply); repo.save(f); return result(f); }
    public FeedbackResult close(UUID id, String reason) { var f=find(id); f.close(reason); repo.save(f); return result(f); }
    private Feedback find(UUID id) { return repo.findById(new FeedbackId(id)).orElseThrow(()->new IllegalArgumentException("Feedback not found: "+id)); }
    private FeedbackResult result(Feedback f) { return new FeedbackResult(f.id().value(), f.status().name()); }
}
