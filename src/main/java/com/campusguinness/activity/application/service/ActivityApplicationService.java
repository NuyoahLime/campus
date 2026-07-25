package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.port.TeacherApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActivityApplicationService {
    private final ActivityApplicationRepository repository;
    private final ActivityRepository activityRepository;
    private final SchoolMembershipQueryPort membershipQueryPort;
    private final TeacherApplicationQueryPort teacherQueryPort;

    public ActivityApplicationService(ActivityApplicationRepository repository,
                                       ActivityRepository activityRepository,
                                       SchoolMembershipQueryPort membershipQueryPort,
                                       TeacherApplicationQueryPort teacherQueryPort) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.membershipQueryPort = membershipQueryPort;
        this.teacherQueryPort = teacherQueryPort;
    }

    /** Submit a new application as a TEACHER with active membership in the school. */
    public ActivityApplicationResult submit(SubmitActivityApplicationCommand cmd, UUID applicantId) {
        if (!membershipQueryPort.hasActiveTeacherMembership(applicantId, cmd.schoolId())) {
            throw new IllegalStateException("No active TEACHER membership for this school");
        }

        var app = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID()))
                .schoolId(cmd.schoolId())
                .applicantId(applicantId)
                .title(cmd.title())
                .description(cmd.description()));
        app.submit();
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Approve a SUBMITTED application: create Activity + approve in same transaction. */
    public ActivityApplicationResult approve(UUID id, UUID reviewerId) {
        var app = findById(id);

        if (app.createdActivityId() != null) {
            throw new IllegalStateException("Application already has a created Activity");
        }

        // Generate activity ID and validate state transition before creating Activity
        UUID activityId = UUID.randomUUID();
        app.approve(reviewerId, activityId);

        // Create Activity with DRAFT + NOT_SUBMITTED in same transaction
        var activity = Activity.create(new Activity.Builder()
                .id(new ActivityId(activityId))
                .schoolId(app.schoolId())
                .title(app.title())
                .description(app.description())
                .createdBy(app.applicantId()));
        activityRepository.save(activity);

        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Reject a SUBMITTED application. */
    public ActivityApplicationResult reject(UUID id, UUID reviewerId, String reason) {
        var app = findById(id);
        app.reject(reviewerId, reason);
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Withdraw own SUBMITTED application. */
    public ActivityApplicationResult withdraw(UUID id, UUID applicantId) {
        var app = findByIdAndApplicantId(id, applicantId);
        app.withdraw();
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Return REJECTED application to DRAFT (own only). */
    public ActivityApplicationResult returnToDraft(UUID id, UUID applicantId) {
        var app = findByIdAndApplicantId(id, applicantId);
        app.returnToDraft();
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Update title/description of a DRAFT application (own only). */
    public ActivityApplicationResult updateDraft(UUID id, UUID applicantId, String title, String description) {
        if (!membershipQueryPort.hasActiveTeacherMembership(applicantId,
                repository.findById(new ActivityApplicationId(id))
                        .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id))
                        .schoolId())) {
            throw new IllegalStateException("No active TEACHER membership for this school");
        }
        var app = findByIdAndApplicantId(id, applicantId);
        if (title != null) app.updateTitle(title);
        if (description != null) app.updateDescription(description);
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    /** Re-submit a DRAFT application (own only) — requires ACTIVE TEACHER membership. */
    public ActivityApplicationResult resubmit(UUID id, UUID applicantId) {
        var app = findByIdAndApplicantId(id, applicantId);
        if (!membershipQueryPort.hasActiveTeacherMembership(applicantId, app.schoolId())) {
            throw new IllegalStateException("No active TEACHER membership for this school");
        }
        app.submit();
        repository.save(app);
        return ActivityApplicationResult.fromDomain(app);
    }

    // ── Query methods ──

    @Transactional(readOnly = true)
    public List<ActivityApplicationResult> listMine(UUID applicantId) {
        return repository.findByApplicantId(applicantId).stream()
                .map(ActivityApplicationResult::fromDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivityApplicationResult getMine(UUID id, UUID applicantId) {
        return findByIdAndApplicantIdAsResult(id, applicantId);
    }

    @Transactional(readOnly = true)
    public QueryPage<ActivityApplicationResult> listMinePage(UUID applicantId, String status,
            UUID schoolId, String keyword, int page, int size) {
        return teacherQueryPort.findMine(applicantId, status, schoolId, keyword, page, size);
    }

    @Transactional(readOnly = true)
    public ActivityApplicationResult getById(UUID id) {
        return repository.findById(new ActivityApplicationId(id))
                .map(ActivityApplicationResult::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id));
    }

    // ── Internal helpers ──

    private ActivityApplication findById(UUID id) {
        return repository.findById(new ActivityApplicationId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id));
    }

    private ActivityApplication findByIdAndApplicantId(UUID id, UUID applicantId) {
        return repository.findByIdAndApplicantId(id, applicantId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id));
    }

    private ActivityApplicationResult findByIdAndApplicantIdAsResult(UUID id, UUID applicantId) {
        return ActivityApplicationResult.fromDomain(findByIdAndApplicantId(id, applicantId));
    }
}
