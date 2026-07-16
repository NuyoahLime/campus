package com.campusguinness.media.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Media aggregate")
class MediaTest {

    private Media.Builder validBuilder() {
        return new Media.Builder()
                .id(new MediaId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .activityId(UUID.randomUUID())
                .uploaderId(UUID.randomUUID())
                .fileKey("activities/school-uuid/photos/abc123.jpg")
                .fileName("team_photo.jpg")
                .fileType("IMAGE")
                .fileFormat("JPG")
                .fileSizeBytes(2_500_000)
                .checksum("sha256:abc123def456");
    }

    private Media createDraft() {
        return Media.create(validBuilder());
    }

    private Media createInternalApproved() {
        var m = createDraft();
        m.submitForInternalReview();
        m.approveInternal();
        return m;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("creates in DRAFT + NOT_SUBMITTED")
        void shouldCreateInDraftAndNotSubmitted() {
            var m = createDraft();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.DRAFT);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
        }

        @Test
        @DisplayName("null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> Media.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> Media.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null activityId rejected")
        void shouldRejectNullActivityId() {
            assertThatThrownBy(() -> Media.create(validBuilder().activityId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null fileKey rejected")
        void shouldRejectNullFileKey() {
            assertThatThrownBy(() -> Media.create(validBuilder().fileKey(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("fileSizeBytes must be > 0")
        void shouldRejectZeroFileSize() {
            assertThatThrownBy(() -> Media.create(validBuilder().fileSizeBytes(0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("internal_status transitions")
    class InternalTransitions {

        @Test
        @DisplayName("DRAFT → PENDING_INTERNAL_REVIEW")
        void shouldSubmitForInternalReview() {
            var m = createDraft();
            m.submitForInternalReview();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.PENDING_INTERNAL_REVIEW);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaInternalReviewSubmitted);
        }

        @Test
        @DisplayName("PENDING_INTERNAL_REVIEW → INTERNAL_APPROVED")
        void shouldApproveInternal() {
            var m = createDraft();
            m.submitForInternalReview();
            m.approveInternal();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_APPROVED);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaInternalApproved);
        }

        @Test
        @DisplayName("PENDING_INTERNAL_REVIEW → INTERNAL_REJECTED (resets public)")
        void shouldRejectInternal() {
            var m = createDraft();
            m.submitForInternalReview();
            m.rejectInternal();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_REJECTED);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaInternalRejected);
        }

        @Test
        @DisplayName("INTERNAL_REJECTED → DRAFT")
        void shouldReturnToDraft() {
            var m = createDraft();
            m.submitForInternalReview();
            m.rejectInternal();
            m.returnToDraft();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.DRAFT);
        }

        @Test
        @DisplayName("INTERNAL_APPROVED → INTERNAL_DISABLED (no public impact)")
        void shouldDisableInternal() {
            var m = createInternalApproved();
            m.disableInternal();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_DISABLED);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaInternalDisabled);
        }

        @Test
        @DisplayName("INTERNAL_APPROVED → INTERNAL_DISABLED + PUBLIC → auto PLATFORM_TAKEDOWN")
        void disableInternalWhenPublicTriggersTakedown() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformApprove();
            m.makePublic();
            m.disableInternal();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_DISABLED);
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaPlatformTakedown);
        }

        @Test
        @DisplayName("INTERNAL_DISABLED → INTERNAL_APPROVED (no auto-restore public)")
        void shouldReEnableInternal() {
            var m = createInternalApproved();
            m.disableInternal();
            m.reEnableInternal();
            assertThat(m.internalStatus()).isEqualTo(MediaInternalStatus.INTERNAL_APPROVED);
            // public stays NOT_SUBMITTED (not auto-restored)
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("public_status transitions")
    class PublicTransitions {

        @Test
        @DisplayName("NOT_SUBMITTED → PENDING_PUBLIC_REVIEW (only from INTERNAL_APPROVED)")
        void shouldSubmitForPublicReview() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PENDING_PUBLIC_REVIEW);
        }

        @Test
        @DisplayName("DRAFT cannot submit for public review")
        void shouldRejectPublicSubmitFromDraft() {
            var m = createDraft();
            assertThatThrownBy(m::submitForPublicReview)
                    .isInstanceOf(InvalidMediaStateTransitionException.class);
        }

        @Test
        @DisplayName("PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED")
        void shouldPlatformApprove() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformApprove();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PLATFORM_APPROVED);
        }

        @Test
        @DisplayName("PENDING_PUBLIC_REVIEW → PLATFORM_REJECTED")
        void shouldPlatformReject() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformReject();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PLATFORM_REJECTED);
        }

        @Test
        @DisplayName("PLATFORM_REJECTED → NOT_SUBMITTED")
        void shouldReturnToNotSubmitted() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformReject();
            m.returnToNotSubmitted();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
        }

        @Test
        @DisplayName("PLATFORM_REJECTED → PENDING_PUBLIC_REVIEW (direct resubmit)")
        void shouldResubmitForPublicReview() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformReject();
            m.resubmitForPublicReview();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PENDING_PUBLIC_REVIEW);
        }

        @Test
        @DisplayName("PLATFORM_APPROVED → PUBLIC")
        void shouldMakePublic() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformApprove();
            m.makePublic();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PUBLIC);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaMadePublic);
        }

        @Test
        @DisplayName("PUBLIC → PLATFORM_TAKEDOWN")
        void shouldPlatformTakedown() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformApprove();
            m.makePublic();
            m.platformTakedown();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(m.domainEvents()).anyMatch(e -> e instanceof MediaPlatformTakedown);
        }

        @Test
        @DisplayName("PLATFORM_TAKEDOWN → NOT_SUBMITTED")
        void shouldReturnToNotSubmittedFromTakedown() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            m.platformApprove();
            m.makePublic();
            m.platformTakedown();
            m.returnToNotSubmitted();
            assertThat(m.publicStatus()).isEqualTo(MediaPublicStatus.NOT_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("Cannot approve internal from DRAFT")
        void shouldRejectApproveFromDraft() {
            var m = createDraft();
            assertThatThrownBy(m::approveInternal)
                    .isInstanceOf(InvalidMediaStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot disable from DRAFT")
        void shouldRejectDisableFromDraft() {
            var m = createDraft();
            assertThatThrownBy(m::disableInternal)
                    .isInstanceOf(InvalidMediaStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot re-enable from INTERNAL_APPROVED")
        void shouldRejectReEnableFromApproved() {
            var m = createInternalApproved();
            assertThatThrownBy(m::reEnableInternal)
                    .isInstanceOf(InvalidMediaStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot makePublic before platform approval")
        void shouldRejectMakePublicBeforeApproval() {
            var m = createInternalApproved();
            m.submitForPublicReview();
            assertThatThrownBy(m::makePublic)
                    .isInstanceOf(InvalidMediaStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var m = createDraft();
            assertThatThrownBy(() -> m.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
