package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
class FeedbackApplicationServiceTest {
    @Mock FeedbackRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolResourceAuthorization schoolAuthorization;
    @Mock StudentResourceAuthorization studentAuthorization;
    FeedbackApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() {
        actorUserId=UUID.randomUUID();
        lenient().when(currentActor.requireUserId()).thenReturn(actorUserId);
        lenient().when(schoolAuthorization.requireSchoolAdmin(any())).thenReturn(actorUserId);
        lenient().when(studentAuthorization.requireSelf(any())).thenReturn(actorUserId);
        svc = new FeedbackApplicationService(repo, currentActor, schoolAuthorization, studentAuthorization);
    }
    private Feedback fb() { return Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID())).schoolId(UUID.randomUUID()).submitterId(UUID.randomUUID()).feedbackType("GENERAL").content("t")); }

    @Nested class Submit { @Test void success() { assertThat(svc.submit(UUID.randomUUID(),"GENERAL","t").status()).isEqualTo("SUBMITTED"); var captor=forClass(Feedback.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().submitterId()).isEqualTo(actorUserId); } }
    @Nested class BeginProcessing { @Test void success() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(f.id().value(),UUID.randomUUID()).status()).isEqualTo("PROCESSING"); verify(schoolAuthorization).requireSchoolAdmin(f.schoolId()); var captor=forClass(Feedback.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().handlerId()).isEqualTo(actorUserId); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.beginProcessing(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); } }
    @Nested class Resolve { @Test void success() { var f=fb(); f.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.resolve(f.id().value(),"done").status()).isEqualTo("RESOLVED"); verify(schoolAuthorization).requireSchoolAdmin(f.schoolId()); verify(repo).save(any()); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.resolve(UUID.randomUUID(),"done")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); } }
    @Nested class Close { @Test void success() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.close(f.id().value(),"done").status()).isEqualTo("CLOSED"); verify(studentAuthorization).requireSelf(f.submitterId()); verify(repo).save(any()); } }
}
