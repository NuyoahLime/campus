package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.notification.application.service.NotificationService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FeedbackApplicationService {
    private final FeedbackRepository repo;
    private final NotificationService notificationService;

    public FeedbackApplicationService(FeedbackRepository r, NotificationService ns) {
        this.repo = r; this.notificationService = ns;
    }

    public FeedbackResult submit(ActorContext actor, String feedbackType, String content) {
        UUID schoolId = requireSchoolBound(actor);
        var f = Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID()))
                .schoolId(schoolId).submitterId(actor.userId()).feedbackType(feedbackType).content(content));
        repo.save(f);
        return result(f);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResult> listManageable(ActorContext actor, UUID requestedSchoolId) {
        if (actor.isSuperAdmin()) {
            if (requestedSchoolId == null) throw new IllegalArgumentException("schoolId required");
            return map(repo.findBySchoolId(requestedSchoolId));
        }
        if (!actor.isSchoolAdmin()) throw new AccessDeniedException("School administrator role required");
        return map(repo.findBySchoolId(actor.requireSchoolId()));
    }

    public FeedbackResult beginProcessing(UUID id, ActorContext actor) {
        var f = findManageable(id, actor); f.beginProcessing(actor.userId()); repo.save(f); return result(f);
    }

    public FeedbackResult resolve(UUID id, ActorContext actor, String reply) {
        var f = findManageable(id, actor); f.resolve(reply); repo.save(f);
        notificationService.notify(f.submitterId(), "FEEDBACK_RESOLVED", "Feedback Resolved", "Your feedback has been resolved", "FEEDBACK", f.id().value());
        return result(f);
    }

    public FeedbackResult close(UUID id, ActorContext actor, String reason) {
        var f = findManageable(id, actor); f.close(reason); repo.save(f); return result(f);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResult> listMine(UUID submitterId) {
        return map(repo.findBySubmitterId(submitterId));
    }

    @Transactional(readOnly = true)
    public FeedbackResult getMine(UUID id, UUID submitterId) {
        return repo.findByIdAndSubmitterId(id, submitterId)
                .map(f -> new FeedbackResult(f.id().value(), f.status().name()))
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + id));
    }

    private Feedback findManageable(UUID id, ActorContext actor) {
        if (actor.isSuperAdmin()) return repo.findById(new FeedbackId(id))
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found"));
        if (!actor.isSchoolAdmin()) throw new AccessDeniedException("School administrator role required");
        if (actor.primarySchoolId() == null) throw new AccessDeniedException("School context required");
        return repo.findByIdAndSchoolId(id, actor.primarySchoolId())
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found"));
    }

    private UUID requireSchoolBound(ActorContext actor) {
        if (actor.isSuperAdmin()) throw new AccessDeniedException("School-bound identity required");
        UUID schoolId = actor.primarySchoolId();
        if (schoolId == null) throw new AccessDeniedException("School context required");
        return schoolId;
    }

    private List<FeedbackResult> map(List<Feedback> items) {
        return items.stream().map(f -> new FeedbackResult(f.id().value(), f.status().name())).toList();
    }

    private FeedbackResult result(Feedback f) { return new FeedbackResult(f.id().value(), f.status().name()); }
}
