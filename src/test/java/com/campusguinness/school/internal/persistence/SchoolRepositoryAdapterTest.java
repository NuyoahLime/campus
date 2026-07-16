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
@DisplayName("SchoolRepositoryAdapter")
class SchoolRepositoryAdapterTest {

    @Mock private SchoolJpaRepository jpaRepository;
    @InjectMocks private SchoolRepositoryAdapter adapter;

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("saves domain via JpaRepository")
        void shouldSave() {
            var s = School.create(new School.Builder().id(new SchoolId(UUID.randomUUID()))
                    .name("test").unifiedCodeType("USCC").unifiedCode("123")
                    .internalCode("INT-001").schoolType("PRIMARY").region("Beijing")
                    .address("addr").contactName("n").contactPhone("p").contactEmail("e"));
            adapter.save(s);
            verify(jpaRepository).save(any(SchoolEntity.class));
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns empty when not found")
        void shouldReturnEmpty() {
            when(jpaRepository.findById(any())).thenReturn(Optional.empty());
            assertThat(adapter.findById(new SchoolId(UUID.randomUUID()))).isEmpty();
        }

        @Test @DisplayName("restores DISABLED without events")
        void shouldRestoreDisabled() {
            UUID id = UUID.randomUUID();
            var e = buildEntity(id, "DISABLED");
            when(jpaRepository.findById(id)).thenReturn(Optional.of(e));
            var s = adapter.findById(new SchoolId(id));
            assertThat(s).isPresent();
            assertThat(s.get().status()).isEqualTo(SchoolStatus.DISABLED);
            assertThat(s.get().domainEvents()).isEmpty();
        }
    }

    private SchoolEntity buildEntity(UUID id, String status) {
        var e = new SchoolEntity();
        e.setId(id); e.setName("test"); e.setUnifiedCodeType("USCC");
        e.setUnifiedCode("123"); e.setInternalCode("INT-001"); e.setSchoolType("PRIMARY");
        e.setRegion("Beijing"); e.setAddress("addr"); e.setContactName("n");
        e.setContactPhone("p"); e.setContactEmail("e"); e.setSchoolStatus(status);
        return e;
    }
}
