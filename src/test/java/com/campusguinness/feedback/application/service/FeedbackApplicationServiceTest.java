package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.notification.application.service.NotificationService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackApplicationServiceTest {
    @Mock FeedbackRepository repo;
    @Mock NotificationService notificationService;
    FeedbackApplicationService svc;

    private static final UUID FB_ID = UUID.randomUUID();
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final ActorContext SCHOOL_ADMIN = new ActorContext(USER_ID, "SCHOOL_ADMIN", SCHOOL_ID);
    private static final ActorContext SUPER_ADMIN = new ActorContext(UUID.randomUUID(), "SUPER_ADMIN", null);

    @BeforeEach void setUp() {
        svc = new FeedbackApplicationService(repo, notificationService);
        lenient().doNothing().when(notificationService).notify(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private Feedback fb() { return Feedback.create(new Feedback.Builder().id(new FeedbackId(FB_ID)).schoolId(SCHOOL_ID).submitterId(USER_ID).feedbackType("GENERAL").content("t")); }

    @Nested class Submit {
        @Test void success() { assertThat(svc.submit(SCHOOL_ADMIN, "GENERAL", "t").status()).isEqualTo("SUBMITTED"); verify(repo).save(any()); }
        @Test void superAdminRejected() { assertThatThrownBy(() -> svc.submit(SUPER_ADMIN, "GENERAL", "t")).isInstanceOf(AccessDeniedException.class); }
    }
    @Nested class BeginProcessing {
        @Test void schoolAdminSameSchool() { var f=fb(); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_ID)).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(FB_ID, SCHOOL_ADMIN).status()).isEqualTo("PROCESSING"); verify(repo).save(any()); }
        @Test void superAdminBypasses() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(FB_ID, SUPER_ADMIN).status()).isEqualTo("PROCESSING"); }
    }
    @Nested class Resolve {
        @Test void schoolAdminSameSchool() { var f=fb(); f.beginProcessing(USER_ID); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_ID)).thenReturn(Optional.of(f)); assertThat(svc.resolve(FB_ID, SCHOOL_ADMIN, "done").status()).isEqualTo("RESOLVED"); }
    }
    @Nested class Close {
        @Test void schoolAdminSameSchool() { var f=fb(); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_ID)).thenReturn(Optional.of(f)); assertThat(svc.close(FB_ID, SCHOOL_ADMIN, "done").status()).isEqualTo("CLOSED"); }
    }
}
