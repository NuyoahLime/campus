package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
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
            e.setCurrentCandidateVersionId(UUID.randomUUID());
            e.setCurrentInternalVersionId(UUID.randomUUID());
            e.setCurrentPublicVersionId(UUID.randomUUID());
            e.setPublicVisibilityBlocked(true);
            e.setVersion(7);
            var r = ActivityResultPersistenceMapper.toDomain(e);
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_PUBLISHED);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PUBLIC);
            assertThat(r.currentCandidateVersionId()).isEqualTo(e.getCurrentCandidateVersionId());
            assertThat(r.currentInternalVersionId()).isEqualTo(e.getCurrentInternalVersionId());
            assertThat(r.currentPublicVersionId()).isEqualTo(e.getCurrentPublicVersionId());
            assertThat(r.publicVisibilityBlocked()).isTrue();
            assertThat(r.persistenceVersion()).isEqualTo(7);
            assertThat(r.createdAt()).isEqualTo(e.getCreatedAt());
            assertThat(r.updatedAt()).isEqualTo(e.getUpdatedAt());
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
            UUID candidate = UUID.randomUUID();
            UUID internal = UUID.randomUUID();
            UUID published = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
            var r = ActivityResult.reconstitute(new ActivityResult.Builder()
                            .id(new ActivityResultId(UUID.randomUUID()))
                            .schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()),
                    ResultInternalStatus.INTERNAL_PUBLISHED, ResultPublicStatus.PUBLIC,
                    candidate, internal, published, true, createdAt, updatedAt, 5);
            var e = ActivityResultPersistenceMapper.toEntity(r);
            assertThat(e.getResultInternalStatus()).isEqualTo("INTERNAL_PUBLISHED");
            assertThat(e.getResultPublicStatus()).isEqualTo("PUBLIC");
            assertThat(e.getCurrentCandidateVersionId()).isEqualTo(candidate);
            assertThat(e.getCurrentInternalVersionId()).isEqualTo(internal);
            assertThat(e.getCurrentPublicVersionId()).isEqualTo(published);
            assertThat(e.isPublicVisibilityBlocked()).isTrue();
            assertThat(e.getCreatedAt()).isEqualTo(createdAt);
            assertThat(e.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(e.getVersion()).isEqualTo(5);
        }
    }
    private ActivityResultEntity entity(String internal, String pub) {
        var e = new ActivityResultEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setActivityId(UUID.randomUUID()); e.setResultInternalStatus(internal); e.setResultPublicStatus(pub);
        e.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return e;
    }
}
