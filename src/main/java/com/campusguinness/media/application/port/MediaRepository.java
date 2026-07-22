package com.campusguinness.media.application.port;
import com.campusguinness.media.internal.domain.Media;
import com.campusguinness.media.internal.domain.MediaId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface MediaRepository { void save(Media media); Optional<Media> findById(MediaId id); List<Media> findByActivityId(UUID activityId); List<Media> findBySchoolId(UUID schoolId); }
