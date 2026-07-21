package com.campusguinness.media.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class MediaApplicationService {
    private final MediaRepository repository;
    private final ActivityRepository activityRepository;
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;

    public MediaApplicationService(MediaRepository repository,
                                    ActivityRepository activityRepository,
                                    CurrentActor currentActor,
                                    SchoolMembershipResolver membershipResolver) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
    }

    public MediaResult register(RegisterMediaCommand cmd) {
        // Load the real Activity to verify school binding.
        var activity = activityRepository.findById(new ActivityId(cmd.activityId()))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + cmd.activityId()));

        UUID realSchoolId = activity.schoolId();

        // Cross-check: request schoolId must match the Activity's real schoolId.
        if (!cmd.schoolId().equals(realSchoolId)) {
            throw new IllegalArgumentException(
                    "schoolId " + cmd.schoolId() + " does not match Activity school " + realSchoolId);
        }

        // Authorize: actor must be TEACHER or above at the Activity's real school.
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, cmd.uploaderId(), realSchoolId);
        }

        // Create Media using the verified real schoolId.
        var m = Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID()))
                .schoolId(realSchoolId).activityId(cmd.activityId()).uploaderId(cmd.uploaderId())
                .fileKey(cmd.fileKey()).fileName(cmd.fileName()).fileType(cmd.fileType())
                .fileFormat(cmd.fileFormat()).fileSizeBytes(cmd.fileSizeBytes())
                .checksum(cmd.checksum()).description(cmd.description()));
        repository.save(m);
        return new MediaResult(m.id().value(), m.internalStatus().name(), m.publicStatus().name());
    }

    public MediaResult submitForInternalReview(UUID id) {
        var m = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), m.schoolId());
        }
        m.submitForInternalReview();
        repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    public MediaResult approveInternal(UUID id) {
        var m = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, currentActor.requireUserId(), m.schoolId());
        }
        m.approveInternal();
        repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    private Media find(UUID id) {
        return repository.findById(new MediaId(id))
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + id));
    }
}
