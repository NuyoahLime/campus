package com.campusguinness.media.application.service;

import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MediaApplicationService {
    private final MediaRepository repository;
    public MediaApplicationService(MediaRepository r) { this.repository = r; }

    public MediaResult register(RegisterMediaCommand cmd) {
        var m = Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID()))
                .schoolId(cmd.schoolId()).activityId(cmd.activityId()).uploaderId(cmd.uploaderId())
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
        var m = find(id); m.approveInternal(); repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    public MediaResult makePublic(UUID id) {
        var m = find(id);
        m.platformApprove();
        m.makePublic();
        repository.save(m);
        return new MediaResult(id, m.internalStatus().name(), m.publicStatus().name());
    }

    @Transactional(readOnly = true)
    public List<MediaResult> listPublicByActivity(UUID activityId) {
        return repository.findByActivityId(activityId).stream()
                .filter(m -> m.publicStatus() == MediaPublicStatus.PUBLIC)
                .map(m -> new MediaResult(m.id().value(), m.internalStatus().name(), m.publicStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaResult> listBySchool(UUID schoolId) {
        return repository.findBySchoolId(schoolId).stream()
                .map(m -> new MediaResult(m.id().value(), m.internalStatus().name(), m.publicStatus().name()))
                .toList();
    }

    private Media find(UUID id) {
        return repository.findById(new MediaId(id))
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + id));
    }
}
