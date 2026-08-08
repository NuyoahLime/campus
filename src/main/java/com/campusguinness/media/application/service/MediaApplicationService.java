package com.campusguinness.media.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
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
    private final CurrentActor currentActor;
    private final SchoolResourceAuthorization authorization;

    public MediaApplicationService(MediaRepository r, CurrentActor currentActor,
            SchoolResourceAuthorization authorization) {
        this.repository = r;
        this.currentActor = currentActor;
        this.authorization = authorization;
    }

    public MediaResult register(RegisterMediaCommand cmd) {
        UUID actorUserId = currentActor.requireUserId();
        var m = Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID()))
                .schoolId(cmd.schoolId()).activityId(cmd.activityId()).uploaderId(actorUserId)
                .fileKey(cmd.fileKey()).fileName(cmd.fileName()).fileType(cmd.fileType())
                .fileFormat(cmd.fileFormat()).fileSizeBytes(cmd.fileSizeBytes())
                .checksum(cmd.checksum()).description(cmd.description()));
        repository.save(m);
        return new MediaResult(m.id().value(), m.internalStatus().name(), m.publicStatus().name());
    }

    public MediaResult submitForInternalReview(UUID id) {
        var m = find(id); m.submitForInternalReview(); repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    public MediaResult approveInternal(UUID id) {
        var m = find(id);
        authorization.requireSchoolAdmin(m.schoolId());
        m.approveInternal();
        repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    private Media find(UUID id) {
        return repository.findById(new MediaId(id))
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + id));
    }
}
