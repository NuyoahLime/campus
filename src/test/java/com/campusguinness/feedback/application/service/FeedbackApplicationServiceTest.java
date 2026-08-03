package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.notification.application.service.NotificationService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
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
    private static final UUID SCHOOL_A = UUID.randomUUID();
    private static final UUID SCHOOL_B = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final ActorContext ADMIN_A = new ActorContext(USER_ID, "SCHOOL_ADMIN", SCHOOL_A);
    private static final ActorContext ADMIN_B = new ActorContext(UUID.randomUUID(), "SCHOOL_ADMIN", SCHOOL_B);
    private static final ActorContext SUPER_ADMIN = new ActorContext(UUID.randomUUID(), "SUPER_ADMIN", null);
    private static final ActorContext STUDENT = new ActorContext(UUID.randomUUID(), "STUDENT", SCHOOL_A);

    @BeforeEach void setUp() {
        svc = new FeedbackApplicationService(repo, notificationService);
        lenient().doNothing().when(notificationService).notify(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private Feedback fb() { return Feedback.create(new Feedback.Builder().id(new FeedbackId(FB_ID)).schoolId(SCHOOL_A).submitterId(USER_ID).feedbackType("GENERAL").content("t")); }

    @Nested class Submit {
        @Test void success() {
            assertThat(svc.submit(ADMIN_A, "GENERAL", "t").status()).isEqualTo("SUBMITTED");
            ArgumentCaptor<Feedback> c = ArgumentCaptor.forClass(Feedback.class);
            verify(repo).save(c.capture());
            assertThat(c.getValue().schoolId()).isEqualTo(SCHOOL_A);
        }
        @Test void superAdminRejected() { assertThatThrownBy(() -> svc.submit(SUPER_ADMIN, "GENERAL", "t")).isInstanceOf(AccessDeniedException.class); }
    }

    @Nested class ListManageable {
        @Test void schoolAdminUsesOwnSchool() {
            when(repo.findBySchoolId(SCHOOL_A)).thenReturn(List.of(fb()));
            svc.listManageable(ADMIN_A, UUID.randomUUID()); // ignores requested param
            verify(repo).findBySchoolId(SCHOOL_A);
        }
        @Test void superAdminUsesRequestedSchool() {
            when(repo.findBySchoolId(SCHOOL_B)).thenReturn(List.of());
            svc.listManageable(SUPER_ADMIN, SCHOOL_B);
            verify(repo).findBySchoolId(SCHOOL_B);
        }
        @Test void superAdminWithoutSchoolIdRejected() {
            assertThatThrownBy(() -> svc.listManageable(SUPER_ADMIN, null)).isInstanceOf(IllegalArgumentException.class);
        }
        @Test void studentRejected() {
            assertThatThrownBy(() -> svc.listManageable(STUDENT, SCHOOL_A)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested class BeginProcessing {
        @Test void sameSchool() { var f=fb(); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_A)).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(FB_ID, ADMIN_A).status()).isEqualTo("PROCESSING"); verify(repo).save(any()); }
        @Test void crossSchoolReturns404() { when(repo.findByIdAndSchoolId(eq(FB_ID), any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> svc.beginProcessing(FB_ID, ADMIN_B)).isInstanceOf(IllegalArgumentException.class); verify(repo, never()).save(any()); }
        @Test void superAdminBypasses() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(FB_ID, SUPER_ADMIN).status()).isEqualTo("PROCESSING"); }
    }

    @Nested class Resolve {
        @Test void sameSchool() { var f=fb(); f.beginProcessing(USER_ID); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_A)).thenReturn(Optional.of(f)); assertThat(svc.resolve(FB_ID, ADMIN_A, "done").status()).isEqualTo("RESOLVED"); }
        @Test void crossSchoolReturns404() { when(repo.findByIdAndSchoolId(eq(FB_ID), any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> svc.resolve(FB_ID, ADMIN_B, "done")).isInstanceOf(IllegalArgumentException.class); verify(repo, never()).save(any()); verifyNoInteractions(notificationService); }
    }

    @Nested class Close {
        @Test void sameSchool() { var f=fb(); when(repo.findByIdAndSchoolId(FB_ID, SCHOOL_A)).thenReturn(Optional.of(f)); assertThat(svc.close(FB_ID, ADMIN_A, "done").status()).isEqualTo("CLOSED"); }
        @Test void crossSchoolReturns404() { when(repo.findByIdAndSchoolId(eq(FB_ID), any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> svc.close(FB_ID, ADMIN_B, "done")).isInstanceOf(IllegalArgumentException.class); verify(repo, never()).save(any()); }
    }
}
