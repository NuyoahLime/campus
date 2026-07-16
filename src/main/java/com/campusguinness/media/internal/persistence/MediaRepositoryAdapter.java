package com.campusguinness.media.internal.persistence;

import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.internal.domain.Media;
import com.campusguinness.media.internal.domain.MediaId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class MediaRepositoryAdapter implements MediaRepository {
    private final MediaJpaRepository jpaRepository;
    MediaRepositoryAdapter(MediaJpaRepository r) { this.jpaRepository = r; }
    @Override @Transactional public void save(Media m) { jpaRepository.save(MediaPersistenceMapper.toEntity(m)); }
    @Override @Transactional(readOnly = true) public Optional<Media> findById(MediaId id) {
        return jpaRepository.findById(id.value()).map(MediaPersistenceMapper::toDomain);
    }
}
