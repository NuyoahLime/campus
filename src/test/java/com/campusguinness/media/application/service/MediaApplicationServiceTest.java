package com.campusguinness.media.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaApplicationServiceTest {
    @Mock MediaRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolResourceAuthorization authorization;
    MediaApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() { actorUserId=UUID.randomUUID(); lenient().when(currentActor.requireUserId()).thenReturn(actorUserId); svc = new MediaApplicationService(repo, currentActor, authorization); }

    @Test void shouldRegister() {
        var r = svc.register(new RegisterMediaCommand(UUID.randomUUID(),UUID.randomUUID(),"k","f","IMAGE","JPG",100,null,null));
        assertThat(r.internalStatus()).isEqualTo("DRAFT"); var captor=forClass(Media.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().uploaderId()).isEqualTo(actorUserId);
    }
    @Test void shouldSubmitForInternalReview() {
        var m = media(); when(repo.findById(any())).thenReturn(Optional.of(m));
        assertThat(svc.submitForInternalReview(m.id().value()).internalStatus()).isEqualTo("PENDING_INTERNAL_REVIEW");
        verify(repo).save(any());
    }
    @Test void shouldApproveInternal() {
        var m = media(); m.submitForInternalReview(); when(repo.findById(any())).thenReturn(Optional.of(m));
        assertThat(svc.approveInternal(m.id().value()).internalStatus()).isEqualTo("INTERNAL_APPROVED");
        verify(authorization).requireSchoolAdmin(m.schoolId());
        verify(repo).save(any());
    }
    @Test void shouldThrowWhenNotFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.submitForInternalReview(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
    private Media media() { return Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityId(UUID.randomUUID()).uploaderId(UUID.randomUUID()).fileKey("k").fileName("f").fileType("IMAGE").fileFormat("JPG").fileSizeBytes(1)); }
}
