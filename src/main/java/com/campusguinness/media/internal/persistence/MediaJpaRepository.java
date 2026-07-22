package com.campusguinness.media.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface MediaJpaRepository extends JpaRepository<MediaEntity, UUID> {     List<MediaEntity> findByActivityId(UUID activityId);
    List<MediaEntity> findBySchoolId(UUID schoolId);
}
