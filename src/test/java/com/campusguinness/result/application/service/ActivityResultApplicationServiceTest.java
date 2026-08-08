package com.campusguinness.result.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityResultApplicationServiceTest {
    @Mock ActivityResultRepository repo;
    @Mock SchoolResourceAuthorization authorization;
    ActivityResultApplicationService svc;
    @BeforeEach void setUp() { svc = new ActivityResultApplicationService(repo, authorization); }

    @Test void shouldCreate() {
        var r = svc.create(UUID.randomUUID(), UUID.randomUUID());
        assertThat(r.internalStatus()).isEqualTo("DRAFT");
        verify(repo).save(any());
    }
    @Test void shouldPublishInternal() {
        var result = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()));
        when(repo.findById(any())).thenReturn(Optional.of(result));
        assertThat(svc.publishInternal(result.id().value()).internalStatus()).isEqualTo("INTERNAL_PUBLISHED");
        verify(authorization).requireSchoolAdmin(result.schoolId());
        verify(repo).save(any());
    }
    @Test void shouldThrowWhenNotFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.publishInternal(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
}
