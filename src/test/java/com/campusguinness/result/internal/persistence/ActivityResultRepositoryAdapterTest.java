package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.*;
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
class ActivityResultRepositoryAdapterTest {
    @Mock ActivityResultJpaRepository jpa;
    @InjectMocks ActivityResultRepositoryAdapter adapter;

    @Test void saveCallsJpa() { adapter.save(draft()); verify(jpa).save(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new ActivityResultId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresPublishedNoEvents() {
        var e = entity("INTERNAL_PUBLISHED","NOT_SUBMITTED"); when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        var r = adapter.findById(new ActivityResultId(e.getId()));
        assertThat(r).isPresent(); assertThat(r.get().internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_PUBLISHED);
        assertThat(r.get().domainEvents()).isEmpty();
    }
    private ActivityResult draft() { return ActivityResult.create(new ActivityResult.Builder().id(new ActivityResultId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityId(UUID.randomUUID())); }
    private ActivityResultEntity entity(String i, String p) { var e = new ActivityResultEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setActivityId(UUID.randomUUID()); e.setResultInternalStatus(i); e.setResultPublicStatus(p); return e; }
}
