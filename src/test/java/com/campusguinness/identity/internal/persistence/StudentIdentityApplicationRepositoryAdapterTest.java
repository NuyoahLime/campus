package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentIdentityApplicationRepositoryAdapter")
class StudentIdentityApplicationRepositoryAdapterTest {

    @Mock private StudentIdentityApplicationJpaRepository jpaRepository;
    @InjectMocks private StudentIdentityApplicationRepositoryAdapter adapter;

    @Nested
    @DisplayName("save")
    class Save {
        @Test
        @DisplayName("saves domain via JpaRepository")
        void saves() {
            adapter.save(application());

            verify(jpaRepository).save(any(StudentIdentityApplicationEntity.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {
        @Test
        @DisplayName("returns empty when not found")
        void returnsEmpty() {
            when(jpaRepository.findById(any())).thenReturn(Optional.empty());

            assertThat(adapter.findById(new StudentIdentityApplicationId(UUID.randomUUID()))).isEmpty();
        }

        @Test
        @DisplayName("restores domain when found")
        void restoresDomain() {
            var id = UUID.randomUUID();
            var entity = entity(id, "APPROVED");
            when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));

            var restored = adapter.findById(new StudentIdentityApplicationId(id));

            assertThat(restored).isPresent();
            assertThat(restored.get().status()).isEqualTo(StudentIdentityApplicationStatus.APPROVED);
        }
    }

    private StudentIdentityApplication application() {
        return StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(UUID.randomUUID()))
                .userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .realName("Student Name")
                .studentNumber("S-001")
                .grade("G7")
                .className("Class 1"));
    }

    private StudentIdentityApplicationEntity entity(UUID id, String status) {
        var entity = new StudentIdentityApplicationEntity();
        entity.setId(id);
        entity.setUserId(UUID.randomUUID());
        entity.setSchoolId(UUID.randomUUID());
        entity.setRealName("Student Name");
        entity.setStudentNumber("S-001");
        entity.setGrade("G7");
        entity.setClassName("Class 1");
        entity.setApplicationStatus(status);
        entity.setReviewedBy(UUID.randomUUID());
        entity.setReviewedAt(java.time.Instant.now());
        return entity;
    }
}
