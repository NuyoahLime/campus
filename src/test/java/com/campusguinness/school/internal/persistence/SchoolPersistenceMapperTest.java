package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("SchoolPersistenceMapper")
class SchoolPersistenceMapperTest {

    private SchoolEntity buildEntity(String status) {
        var e = new SchoolEntity();
        e.setId(UUID.randomUUID()); e.setName("test"); e.setUnifiedCodeType("USCC");
        e.setUnifiedCode("123"); e.setInternalCode("INT-001"); e.setSchoolType("PRIMARY");
        e.setRegion("Beijing"); e.setAddress("addr"); e.setContactName("name");
        e.setContactPhone("phone"); e.setContactEmail("email"); e.setSchoolStatus(status);
        return e;
    }

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test @DisplayName("restores NORMAL without events")
        void shouldRestoreNormal() {
            var e = buildEntity("NORMAL");
            var s = SchoolPersistenceMapper.toDomain(e);
            assertThat(s.status()).isEqualTo(SchoolStatus.NORMAL);
            assertThat(s.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores DISABLED without events")
        void shouldRestoreDisabled() {
            var e = buildEntity("DISABLED");
            var s = SchoolPersistenceMapper.toDomain(e);
            assertThat(s.status()).isEqualTo(SchoolStatus.DISABLED);
            assertThat(s.domainEvents()).isEmpty();
        }
    }

    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test @DisplayName("maps domain to entity preserving fields")
        void shouldMapToEntity() {
            var s = School.create(new School.Builder().id(new SchoolId(UUID.randomUUID()))
                    .name("test").unifiedCodeType("USCC").unifiedCode("123")
                    .internalCode("INT-001").schoolType("PRIMARY").region("Beijing")
                    .address("addr").contactName("name").contactPhone("phone").contactEmail("email"));
            var e = SchoolPersistenceMapper.toEntity(s);
            assertThat(e.getId()).isEqualTo(s.id().value());
            assertThat(e.getSchoolStatus()).isEqualTo("PENDING_ENABLE");
        }

        @Test
        void updatePreservesCreatedAtAndVersion() {
            var entity = buildEntity("PENDING_ENABLE");
            var createdAt = java.time.Instant.parse("2026-01-01T00:00:00Z");
            entity.setCreatedAt(createdAt);
            entity.setVersion(3);
            var school = School.reconstitute(new School.Builder()
                    .id(new SchoolId(entity.getId())).name("test").unifiedCodeType("USCC").unifiedCode("123")
                    .internalCode("INT-001").schoolType("PRIMARY").region("Beijing")
                    .address("addr").contactName("name").contactPhone("phone").contactEmail("email")
                    .status(SchoolStatus.NORMAL));

            SchoolPersistenceMapper.updateEntity(entity, school);

            assertThat(entity.getSchoolStatus()).isEqualTo("NORMAL");
            assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
            assertThat(entity.getVersion()).isEqualTo(3);
            assertThat(entity.getUpdatedAt()).isNotNull();
        }
    }
}
