package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolRegistrationRepositoryAdapter")
class SchoolRegistrationRepositoryAdapterTest {

    @Mock private SchoolRegistrationJpaRepository jpaRepository;
    @InjectMocks private SchoolRegistrationRepositoryAdapter adapter;

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("saves domain via JpaRepository")
        void shouldSave() {
            var reg = SchoolRegistration.create(new SchoolRegistration.Builder()
                    .id(new SchoolRegistrationId(UUID.randomUUID())).schoolName("test")
                    .unifiedCodeType("USCC").schoolType("PRIMARY").region("Beijing")
                    .address("addr").contactName("n").contactPhone("p").contactEmail("e"));
            adapter.save(reg);
            verify(jpaRepository).save(any(SchoolRegistrationEntity.class));
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns empty when not found")
        void shouldReturnEmpty() {
            when(jpaRepository.findById(any())).thenReturn(Optional.empty());
            assertThat(adapter.findById(new SchoolRegistrationId(UUID.randomUUID()))).isEmpty();
        }

        @Test @DisplayName("restores APPROVED with audit fields, no events")
        void shouldRestoreApproved() {
            UUID id = UUID.randomUUID(), reviewerId = UUID.randomUUID(), schoolId = UUID.randomUUID();
            var e = buildEntity(id, "APPROVED");
            e.setReviewedBy(reviewerId); e.setReviewComment("ok");
            e.setCreatedSchoolId(schoolId);
            when(jpaRepository.findById(id)).thenReturn(Optional.of(e));
            var r = adapter.findById(new SchoolRegistrationId(id));
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(r.get().reviewedBy()).isEqualTo(reviewerId);
            assertThat(r.get().createdSchoolId()).isEqualTo(schoolId);
            assertThat(r.get().domainEvents()).isEmpty();
        }
    }

    private SchoolRegistrationEntity buildEntity(UUID id, String status) {
        var e = new SchoolRegistrationEntity();
        e.setId(id); e.setSchoolName("test"); e.setUnifiedCodeType("USCC");
        e.setSchoolType("PRIMARY"); e.setRegion("Beijing"); e.setAddress("addr");
        e.setContactName("n"); e.setContactPhone("p"); e.setContactEmail("e");
        e.setRegistrationStatus(status);
        return e;
    }
}
