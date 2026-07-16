package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ActivityResultPersistenceMapper")
class ActivityResultPersistenceMapperTest {

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test void restoresDraftNotSubmitted() {
            var e = entity("DRAFT","NOT_SUBMITTED");
            var r = ActivityResultPersistenceMapper.toDomain(e);
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.DRAFT);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.NOT_SUBMITTED);
            assertThat(r.domainEvents()).isEmpty();
        }
        @Test void restoresInternalPublishedAndPublic() {
            var e = entity("INTERNAL_PUBLISHED","PUBLIC");
            var r = ActivityResultPersistenceMapper.toDomain(e);
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_PUBLISHED);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PUBLIC);
            assertThat(r.domainEvents()).isEmpty();
        }
        @Test void restoresInternalWithdrawnAndTakedown() {
            var e = entity("INTERNAL_WITHDRAWN","PLATFORM_TAKEDOWN");
            var r = ActivityResultPersistenceMapper.toDomain(e);
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_WITHDRAWN);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(r.domainEvents()).isEmpty();
        }
    }
    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test void mapsToEntity() {
            var r = ActivityResult.create(new ActivityResult.Builder()
                    .id(new ActivityResultId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()));
            var e = ActivityResultPersistenceMapper.toEntity(r);
            assertThat(e.getResultInternalStatus()).isEqualTo("DRAFT");
            assertThat(e.getResultPublicStatus()).isEqualTo("NOT_SUBMITTED");
        }
    }
    private ActivityResultEntity entity(String internal, String pub) {
        var e = new ActivityResultEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setActivityId(UUID.randomUUID()); e.setResultInternalStatus(internal); e.setResultPublicStatus(pub);
        return e;
    }
}
