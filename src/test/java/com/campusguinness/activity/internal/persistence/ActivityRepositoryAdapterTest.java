package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRepositoryAdapterTest {
    @Mock ActivityJpaRepository jpaRepository;
    @InjectMocks ActivityRepositoryAdapter adapter;

    @Test @DisplayName("save calls JpaRepository")
    void shouldSave() { adapter.save(draft()); verify(jpaRepository).save(any()); }

    @Test @DisplayName("findById returns empty")
    void shouldReturnEmpty() {
        when(jpaRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(adapter.findById(new ActivityId(UUID.randomUUID()))).isEmpty();
    }

    @Test @DisplayName("restores dual state without events")
    void shouldRestoreDualState() {
        var e = entity("PUBLISHED","NOT_SUBMITTED"); when(jpaRepository.findById(e.getId())).thenReturn(Optional.of(e));
        var a = adapter.findById(new ActivityId(e.getId()));
        assertThat(a).isPresent();
        assertThat(a.get().executionStatus()).isEqualTo(ExecutionStatus.PUBLISHED);
        assertThat(a.get().publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
        assertThat(a.get().domainEvents()).isEmpty();
    }

    private Activity draft() {
        return Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).createdBy(UUID.randomUUID()).title("t"));
    }
    private ActivityEntity entity(String exec, String pub) {
        var e = new ActivityEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setCreatedBy(UUID.randomUUID()); e.setTitle("t"); e.setExecutionStatus(exec); e.setPublicStatus(pub);
        return e;
    }
}
