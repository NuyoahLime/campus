package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ActivityPersistenceMapper")
class ActivityPersistenceMapperTest {

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test @DisplayName("restores dual state PUBLISHED+NOT_SUBMITTED without events")
        void shouldRestorePublishedDualState() {
            var e = buildEntity("PUBLISHED", "NOT_SUBMITTED");
            var a = ActivityPersistenceMapper.toDomain(e);
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.PUBLISHED);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
            assertThat(a.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores dual state IN_PROGRESS+PUBLIC without events")
        void shouldRestoreInProgressPublic() {
            var e = buildEntity("IN_PROGRESS", "PUBLIC");
            var a = ActivityPersistenceMapper.toDomain(e);
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PUBLIC);
            assertThat(a.domainEvents()).isEmpty();
        }
    }
    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test @DisplayName("maps DRAFT+NOT_SUBMITTED to entity")
        void shouldMapToEntity() {
            var a = Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                    .schoolId(UUID.randomUUID()).createdBy(UUID.randomUUID()).title("test"));
            var e = ActivityPersistenceMapper.toEntity(a);
            assertThat(e.getExecutionStatus()).isEqualTo("DRAFT");
            assertThat(e.getPublicStatus()).isEqualTo("NOT_SUBMITTED");
        }
    }
    private ActivityEntity buildEntity(String exec, String pub) {
        var e = new ActivityEntity();
        e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setCreatedBy(UUID.randomUUID());
        e.setTitle("test"); e.setExecutionStatus(exec); e.setPublicStatus(pub);
        return e;
    }
}
