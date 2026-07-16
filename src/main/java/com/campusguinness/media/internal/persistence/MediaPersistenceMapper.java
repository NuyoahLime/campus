package com.campusguinness.media.internal.persistence;

import com.campusguinness.media.internal.domain.*;
import java.time.Instant;

final class MediaPersistenceMapper {
    private MediaPersistenceMapper() {}

    static MediaEntity toEntity(Media domain) {
        var e = new MediaEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setActivityId(domain.activityId()); e.setUploaderId(domain.uploaderId());
        e.setFileKey(domain.fileKey()); e.setFileName(domain.fileName());
        e.setFileType(domain.fileType()); e.setFileFormat(domain.fileFormat());
        e.setFileSizeBytes(domain.fileSizeBytes()); e.setChecksum(domain.checksum());
        e.setInternalStatus(domain.internalStatus().name());
        e.setPublicStatus(domain.publicStatus().name());
        e.setDescription(domain.description());
        e.setUploadedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static Media toDomain(MediaEntity e) {
        return Media.reconstitute(new Media.Builder()
                .id(new MediaId(e.getId())).schoolId(e.getSchoolId())
                .activityId(e.getActivityId()).uploaderId(e.getUploaderId())
                .fileKey(e.getFileKey()).fileName(e.getFileName())
                .fileType(e.getFileType()).fileFormat(e.getFileFormat())
                .fileSizeBytes(e.getFileSizeBytes()).checksum(e.getChecksum())
                .description(e.getDescription()),
                MediaInternalStatus.valueOf(e.getInternalStatus()),
                MediaPublicStatus.valueOf(e.getPublicStatus()));
    }
}
