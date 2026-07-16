package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ActivityApplicationPersistenceMapper")
class ActivityApplicationPersistenceMapperTest {

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test @DisplayName("restores SUBMITTED without events")
        void shouldRestoreSubmitted() {
            var e = buildEntity("SUBMITTED");
            var a = ActivityApplicationPersistenceMapper.toDomain(e);
            assertThat(a.status()).isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(a.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores APPROVED with audit fields, no events")
        void shouldRestoreApproved() {
            var e = buildEntity("APPROVED");
            UUID activityId = UUID.randomUUID(), reviewerId = UUID.randomUUID();
            e.setCreatedActivityId(activityId); e.setReviewedBy(reviewerId); e.setReviewComment("ok");
            var a = ActivityApplicationPersistenceMapper.toDomain(e);
            assertThat(a.status()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(a.createdActivityId()).isEqualTo(activityId);
            assertThat(a.reviewedBy()).isEqualTo(reviewerId);
            assertThat(a.domainEvents()).isEmpty();
        }
    }
    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test @DisplayName("maps DRAFT domain to entity")
        void shouldMapToEntity() {
            var a = ActivityApplication.create(new ActivityApplication.Builder()
                    .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                    .applicantId(UUID.randomUUID()).title("test").description("desc"));
            var e = ActivityApplicationPersistenceMapper.toEntity(a);
            assertThat(e.getId()).isEqualTo(a.id().value());
            assertThat(e.getApplicationStatus()).isEqualTo("DRAFT");
        }
    }
    private ActivityApplicationEntity buildEntity(String status) {
        var e = new ActivityApplicationEntity();
        e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setApplicantId(UUID.randomUUID());
        e.setTitle("test"); e.setApplicationStatus(status); e.setApplicationVersion(1);
        return e;
    }
}
