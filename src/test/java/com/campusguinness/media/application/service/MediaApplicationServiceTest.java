package com.campusguinness.media.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.port.MediaRepository;
import com.campusguinness.media.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaApplicationServiceTest {
    @Mock MediaRepository repo;
    @Mock ActivityRepository activityRepo;
    @Mock CurrentActor currentActor;
    @Mock SchoolMembershipResolver membershipResolver;
    MediaApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new MediaApplicationService(repo, activityRepo, currentActor, membershipResolver);
        lenient().when(currentActor.isSuperAdmin()).thenReturn(false);
        // Default: Activity exists and belongs to schoolId.
        lenient().when(activityRepo.findById(any()))
                .thenReturn(Optional.of(activity(schoolId)));
    }

    @Nested
    class Register {
        @Test
        void shouldRegisterWhenTeacherOrAbove() {
            when(membershipResolver.isTeacherOrAbove(actorId, schoolId)).thenReturn(true);
            var r = svc.register(cmd(schoolId, activityId, actorId));
            assertThat(r.internalStatus()).isEqualTo("DRAFT");
            verify(repo).save(any());
        }

        @Test
        void shouldRegisterWhenSuperAdmin() {
            when(currentActor.isSuperAdmin()).thenReturn(true);
            var r = svc.register(cmd(schoolId, activityId, actorId));
            assertThat(r.internalStatus()).isEqualTo("DRAFT");
            verify(membershipResolver, never()).isTeacherOrAbove(any(), any());
        }

        @Test
        void shouldDenyWhenNotTeacherOrAbove() {
            when(membershipResolver.isTeacherOrAbove(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.register(cmd(schoolId, activityId, actorId)))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void shouldDenyWhenActivityNotFound() {
            when(activityRepo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.register(cmd(schoolId, activityId, actorId)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Activity not found");
            verify(repo, never()).save(any());
        }

        @Test
        void shouldDenyWhenSchoolMismatch() {
            UUID otherSchoolId = UUID.randomUUID();
            // Activity belongs to schoolId, but request uses otherSchoolId.
            assertThatThrownBy(() -> svc.register(cmd(otherSchoolId, activityId, actorId)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match");
            verify(repo, never()).save(any());
        }

        @Test
        void shouldDenyWhenTeacherAtWrongSchool() {
            // Actor is not teacher at Activity's real school.
            when(membershipResolver.isTeacherOrAbove(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.register(cmd(schoolId, activityId, actorId)))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class SubmitForInternalReview {
        @Test
        void shouldSubmitWhenSchoolAdmin() {
            var m = media(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(m));
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThat(svc.submitForInternalReview(m.id().value()).internalStatus())
                    .isEqualTo("PENDING_INTERNAL_REVIEW");
            verify(repo).save(any());
        }

        @Test
        void shouldSubmitWhenSuperAdmin() {
            var m = media(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(m));
            when(currentActor.isSuperAdmin()).thenReturn(true);
            assertThat(svc.submitForInternalReview(m.id().value()).internalStatus())
                    .isEqualTo("PENDING_INTERNAL_REVIEW");
            verify(membershipResolver, never()).isSchoolAdmin(any(), any());
        }

        @Test
        void shouldDenyWhenNotSchoolAdmin() {
            var m = media(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(m));
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.submitForInternalReview(m.id().value()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class ApproveInternal {
        @Test
        void shouldApproveWhenSchoolAdmin() {
            var m = media(schoolId);
            m.submitForInternalReview();
            when(repo.findById(any())).thenReturn(Optional.of(m));
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThat(svc.approveInternal(m.id().value()).internalStatus())
                    .isEqualTo("INTERNAL_APPROVED");
            verify(repo).save(any());
        }

        @Test
        void shouldDenyWhenCrossSchool() {
            UUID otherSchoolId = UUID.randomUUID();
            var m = media(schoolId);
            m.submitForInternalReview();
            when(repo.findById(any())).thenReturn(Optional.of(m));
            when(currentActor.requireUserId()).thenReturn(actorId);
            lenient().when(membershipResolver.isSchoolAdmin(actorId, otherSchoolId)).thenReturn(true);
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.approveInternal(m.id().value()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class Errors {
        @Test
        void shouldThrowWhenMediaNotFound() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submitForInternalReview(UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }

    private Activity activity(UUID sid) {
        return Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(activityId))
                .schoolId(sid)
                .createdBy(UUID.randomUUID())
                .title("Test Activity")
                .executionStatus(ExecutionStatus.PUBLISHED)
                .publicStatus(PublicStatus.NOT_SUBMITTED));
    }

    private RegisterMediaCommand cmd(UUID sid, UUID aid, UUID uid) {
        return new RegisterMediaCommand(sid, aid, uid, "k", "f", "IMAGE", "JPG", 100, null, null);
    }

    private Media media(UUID sid) {
        return Media.create(new Media.Builder().id(new MediaId(UUID.randomUUID()))
                .schoolId(sid).activityId(UUID.randomUUID()).uploaderId(UUID.randomUUID())
                .fileKey("k").fileName("f").fileType("IMAGE").fileFormat("JPG").fileSizeBytes(1));
    }
}
