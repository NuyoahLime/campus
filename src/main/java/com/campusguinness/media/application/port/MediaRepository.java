package com.campusguinness.media.application.port;
import com.campusguinness.media.internal.domain.Media;
import com.campusguinness.media.internal.domain.MediaId;
import java.util.Optional;
public interface MediaRepository { void save(Media media); Optional<Media> findById(MediaId id); }
