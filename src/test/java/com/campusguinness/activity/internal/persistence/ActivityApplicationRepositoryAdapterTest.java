package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
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
class ActivityApplicationRepositoryAdapterTest {
    @Mock ActivityApplicationJpaRepository jpaRepository;
    @InjectMocks ActivityApplicationRepositoryAdapter adapter;

    @Test @DisplayName("save calls JpaRepository")
    void shouldSave() { adapter.save(draft()); verify(jpaRepository).save(any()); }

    @Test @DisplayName("findById returns empty")
    void shouldReturnEmpty() {
        when(jpaRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(adapter.findById(new ActivityApplicationId(UUID.randomUUID()))).isEmpty();
    }

    @Test @DisplayName("restores APPROVED without events")
    void shouldRestoreApproved() {
        var e = entity("APPROVED"); when(jpaRepository.findById(e.getId())).thenReturn(Optional.of(e));
        var a = adapter.findById(new ActivityApplicationId(e.getId()));
        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(a.get().domainEvents()).isEmpty();
    }

    private ActivityApplication draft() {
        return ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                .applicantId(UUID.randomUUID()).title("t").description("d"));
    }
    private ActivityApplicationEntity entity(String s) {
        var e = new ActivityApplicationEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setApplicantId(UUID.randomUUID()); e.setTitle("t"); e.setApplicationStatus(s); e.setApplicationVersion(1);
        return e;
    }
}
