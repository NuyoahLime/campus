package com.campusguinness.media.internal.persistence;

import com.campusguinness.media.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("MediaPersistenceMapper")
class MediaPersistenceMapperTest {

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test void restoresInternalRejected() {
            var e = entity("INTERNAL_REJECTED","NOT_SUBMITTED");
            var m = MediaPersistenceMapper.toDomain(e);
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_REJECTED);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
            assertThat(m.domainEvents()).isEmpty();
        }
        @Test void restoresInternalApprovedAndPublic() {
            var e = entity("INTERNAL_APPROVED","PUBLIC");
            var m = MediaPersistenceMapper.toDomain(e);
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_APPROVED);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PUBLIC);
            assertThat(m.domainEvents()).isEmpty();
        }
        @Test void restoresDisabledAndTakedown() {
            var e = entity("INTERNAL_DISABLED","PLATFORM_TAKEDOWN");
            var m = MediaPersistenceMapper.toDomain(e);
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_DISABLED);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(m.domainEvents()).isEmpty();
        }
    }
    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test void mapsToEntity() {
            var m = create();
            var e = MediaPersistenceMapper.toEntity(m);
            assertThat(e.getInternalStatus()).isEqualTo("DRAFT");
            assertThat(e.getPublicStatus()).isEqualTo("NOT_SUBMITTED");
            assertThat(e.getFileKey()).isEqualTo("key");
        }
    }
    private MediaEntity entity(String i, String p) {
        var e = new MediaEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setActivityId(UUID.randomUUID()); e.setUploaderId(UUID.randomUUID());
        e.setFileKey("key"); e.setFileName("f"); e.setFileType("IMAGE"); e.setFileFormat("JPG");
        e.setFileSizeBytes(100); e.setInternalStatus(i); e.setPublicStatus(p);
        return e;
    }
    private Media create() {
        return Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()).uploaderId(UUID.randomUUID())
                .fileKey("key").fileName("f").fileType("IMAGE").fileFormat("JPG").fileSizeBytes(100));
    }
}
