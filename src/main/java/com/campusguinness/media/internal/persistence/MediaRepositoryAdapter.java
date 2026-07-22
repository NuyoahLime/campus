package com.campusguinness.media.internal.persistence;

import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.internal.domain.Media;
import com.campusguinness.media.internal.domain.MediaId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class MediaRepositoryAdapter implements MediaRepository {
    private final MediaJpaRepository jpa;
    MediaRepositoryAdapter(MediaJpaRepository r) { this.jpa = r; }

    @Override @Transactional
    public void save(Media m) { jpa.save(MediaPersistenceMapper.toEntity(m)); }

    @Override @Transactional(readOnly = true)
    public Optional<Media> findById(MediaId id) {
        return jpa.findById(id.value()).map(MediaPersistenceMapper::toDomain);
    }

    @Override @Transactional(readOnly = true)
    public List<Media> findByActivityId(UUID activityId) {
        return jpa.findByActivityId(activityId).stream().map(MediaPersistenceMapper::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<Media> findBySchoolId(UUID schoolId) {
        return jpa.findBySchoolId(schoolId).stream().map(MediaPersistenceMapper::toDomain).toList();
    }
}
