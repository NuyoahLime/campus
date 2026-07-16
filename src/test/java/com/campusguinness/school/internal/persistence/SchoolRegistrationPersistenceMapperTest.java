package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("SchoolRegistrationPersistenceMapper")
class SchoolRegistrationPersistenceMapperTest {

    private SchoolRegistrationEntity buildEntity(String status) {
        var e = new SchoolRegistrationEntity();
        e.setId(UUID.randomUUID()); e.setSchoolName("test"); e.setUnifiedCodeType("USCC");
        e.setSchoolType("PRIMARY"); e.setRegion("Beijing"); e.setAddress("addr");
        e.setContactName("name"); e.setContactPhone("phone"); e.setContactEmail("email");
        e.setRegistrationStatus(status);
        e.setReviewedBy(UUID.randomUUID()); e.setReviewComment("looks good");
        e.setCreatedSchoolId(status.equals("APPROVED") ? UUID.randomUUID() : null);
        return e;
    }

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test @DisplayName("restores APPROVED with audit fields and no events")
        void shouldRestoreApproved() {
            var e = buildEntity("APPROVED");
            var r = SchoolRegistrationPersistenceMapper.toDomain(e);
            assertThat(r.status()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(r.createdSchoolId()).isNotNull();
            assertThat(r.reviewedBy()).isNotNull();
            assertThat(r.reviewComment()).isEqualTo("looks good");
            assertThat(r.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores REJECTED with no events")
        void shouldRestoreRejected() {
            var e = buildEntity("REJECTED");
            e.setRejectReason("incomplete");
            var r = SchoolRegistrationPersistenceMapper.toDomain(e);
            assertThat(r.status()).isEqualTo(RegistrationStatus.REJECTED);
            assertThat(r.rejectReason()).isEqualTo("incomplete");
            assertThat(r.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores WITHDRAWN with no events")
        void shouldRestoreWithdrawn() {
            var e = buildEntity("WITHDRAWN");
            var r = SchoolRegistrationPersistenceMapper.toDomain(e);
            assertThat(r.status()).isEqualTo(RegistrationStatus.WITHDRAWN);
            assertThat(r.domainEvents()).isEmpty();
        }
    }

    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test @DisplayName("maps domain to entity preserving fields")
        void shouldMapToEntity() {
            var r = SchoolRegistration.create(new SchoolRegistration.Builder()
                    .id(new SchoolRegistrationId(UUID.randomUUID())).schoolName("test")
                    .unifiedCodeType("USCC").schoolType("PRIMARY").region("Beijing")
                    .address("addr").contactName("name").contactPhone("phone").contactEmail("email"));
            var e = SchoolRegistrationPersistenceMapper.toEntity(r);
            assertThat(e.getId()).isEqualTo(r.id().value());
            assertThat(e.getRegistrationStatus()).isEqualTo("DRAFT");
        }
    }
}
