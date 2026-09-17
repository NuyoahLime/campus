package com.campusguinness.media.application.service;

import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.internal.domain.MediaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MediaEligibilityValidator {
    private final MediaRepository mediaRepository;

    public MediaEligibilityValidator(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional(readOnly = true)
    public void requireEligibleForActivity(List<UUID> mediaIds, UUID schoolId, UUID activityId) {
        Objects.requireNonNull(mediaIds, "mediaIds required");
        Objects.requireNonNull(schoolId, "schoolId required");
        Objects.requireNonNull(activityId, "activityId required");

        for (UUID mediaId : mediaIds) {
            boolean eligible = mediaRepository.findById(new MediaId(mediaId))
                    .filter(media -> schoolId.equals(media.schoolId()))
                    .filter(media -> activityId.equals(media.activityId()))
                    .isPresent();
            if (!eligible) {
                throw new IllegalArgumentException("invalid or ineligible media reference");
            }
        }
    }
}
