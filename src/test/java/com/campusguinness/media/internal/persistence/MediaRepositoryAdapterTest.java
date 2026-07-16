package com.campusguinness.media.internal.persistence;

import com.campusguinness.media.internal.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaRepositoryAdapterTest {
    @Mock MediaJpaRepository jpa;
    @InjectMocks MediaRepositoryAdapter adapter;

    @Test void save() { adapter.save(media()); verify(jpa).save(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new MediaId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresDualStateNoEvents() {
        var e = entity("INTERNAL_APPROVED","PUBLIC"); when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        var m = adapter.findById(new MediaId(e.getId()));
        assertThat(m).isPresent(); assertThat(m.get().internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_APPROVED);
        assertThat(m.get().domainEvents()).isEmpty();
    }
    private Media media() { return Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()).uploaderId(UUID.randomUUID()).fileKey("k").fileName("f").fileType("IMAGE").fileFormat("JPG").fileSizeBytes(1)); }
    private MediaEntity entity(String i, String p) { var e = new MediaEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setActivityId(UUID.randomUUID()); e.setUploaderId(UUID.randomUUID()); e.setFileKey("k"); e.setFileName("f"); e.setFileType("IMAGE"); e.setFileFormat("JPG"); e.setFileSizeBytes(1); e.setInternalStatus(i); e.setPublicStatus(p); return e; }
}
