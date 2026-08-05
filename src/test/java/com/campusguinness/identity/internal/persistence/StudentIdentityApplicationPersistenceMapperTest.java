package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StudentIdentityApplicationPersistenceMapper")
class StudentIdentityApplicationPersistenceMapperTest {

    @Nested
    @DisplayName("Domain to Entity")
    class ToEntity {
        @Test
        @DisplayName("maps fields and PENDING status")
        void mapsDomain() {
            var application = pendingApplication();

            var entity = StudentIdentityApplicationPersistenceMapper.toEntity(application);

            assertThat(entity.getId()).isEqualTo(application.id().value());
            assertThat(entity.getUserId()).isEqualTo(application.userId());
            assertThat(entity.getSchoolId()).isEqualTo(application.schoolId());
            assertThat(entity.getApplicationStatus()).isEqualTo("PENDING");
            assertThat(entity.getEvidenceFileKey()).isEqualTo("proof.pdf");
        }
    }

    @Nested
    @DisplayName("Entity to Domain")
    class ToDomain {
        @Test
        @DisplayName("restores REJECTED with review fields")
        void restoresRejected() {
            var reviewedAt = Instant.parse("2026-08-05T10:15:30Z");
            var reviewerId = UUID.randomUUID();
            var entity = entity("REJECTED");
            entity.setReviewedBy(reviewerId);
            entity.setReviewedAt(reviewedAt);
            entity.setRejectionReason("not enough proof");

            var domain = StudentIdentityApplicationPersistenceMapper.toDomain(entity);

            assertThat(domain.status()).isEqualTo(StudentIdentityApplicationStatus.REJECTED);
            assertThat(domain.reviewedBy()).isEqualTo(reviewerId);
            assertThat(domain.reviewedAt()).isEqualTo(reviewedAt);
            assertThat(domain.rejectionReason()).isEqualTo("not enough proof");
        }
    }

    private StudentIdentityApplication pendingApplication() {
        return StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(UUID.randomUUID()))
                .userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .realName("Student Name")
                .studentNumber("S-001")
                .grade("G7")
                .className("Class 1")
                .evidenceFileKey("proof.pdf"));
    }

    private StudentIdentityApplicationEntity entity(String status) {
        var entity = new StudentIdentityApplicationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setSchoolId(UUID.randomUUID());
        entity.setRealName("Student Name");
        entity.setStudentNumber("S-001");
        entity.setGrade("G7");
        entity.setClassName("Class 1");
        entity.setApplicationStatus(status);
        return entity;
    }
}
