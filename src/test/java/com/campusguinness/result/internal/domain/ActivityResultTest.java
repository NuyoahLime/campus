package com.campusguinness.result.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActivityResult aggregate")
class ActivityResultTest {

    private ActivityResult.Builder validBuilder() {
        return new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .activityId(UUID.randomUUID());
    }

    private ActivityResult createDraft() {
        return ActivityResult.create(validBuilder());
    }

    private ActivityResult createInternalPublished() {
        var r = createDraft();
        r.publishInternal();
        return r;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("creates in DRAFT + NOT_SUBMITTED")
        void shouldCreateInDraftAndNotSubmitted() {
            var r = createDraft();
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.DRAFT);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.NOT_SUBMITTED);
        }

        @Test
        @DisplayName("null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> ActivityResult.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> ActivityResult.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null activityId rejected")
        void shouldRejectNullActivityId() {
            assertThatThrownBy(() -> ActivityResult.create(validBuilder().activityId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("result_internal_status transitions")
    class InternalTransitions {

        @Test
        @DisplayName("DRAFT → INTERNAL_PUBLISHED")
        void shouldPublishInternal() {
            var r = createDraft();
            r.publishInternal();
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_PUBLISHED);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultInternalPublished);
        }

        @Test
        @DisplayName("INTERNAL_PUBLISHED → INTERNAL_WITHDRAWN")
        void shouldWithdrawInternal() {
            var r = createInternalPublished();
            r.withdrawInternal();
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_WITHDRAWN);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultInternalWithdrawn);
        }

        @Test
        @DisplayName("withdrawInternal + PUBLIC → auto PLATFORM_TAKEDOWN")
        void withdrawInternalWhenPublicTriggersTakedown() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            r.makePublic();
            r.withdrawInternal();
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.INTERNAL_WITHDRAWN);
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultPlatformTakenDown);
        }

        @Test
        @DisplayName("INTERNAL_WITHDRAWN → DRAFT (no auto-restore public)")
        void shouldReturnToDraft() {
            var r = createInternalPublished();
            r.withdrawInternal();
            r.returnToDraft();
            assertThat(r.internalStatus()).isEqualTo(ResultInternalStatus.DRAFT);
            // public status remains what it was (NOT_SUBMITTED in this case)
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.NOT_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("result_public_status transitions")
    class PublicTransitions {

        @Test
        @DisplayName("NOT_SUBMITTED → PENDING_PUBLIC_REVIEW (only from INTERNAL_PUBLISHED)")
        void shouldSubmitForReview() {
            var r = createInternalPublished();
            r.submitForReview();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PENDING_PUBLIC_REVIEW);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultSubmittedForReview);
        }

        @Test
        @DisplayName("DRAFT cannot submit for review")
        void shouldRejectSubmitFromDraft() {
            var r = createDraft();
            assertThatThrownBy(r::submitForReview)
                    .isInstanceOf(InvalidResultStateTransitionException.class);
        }

        @Test
        @DisplayName("PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED")
        void shouldPlatformApprove() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PLATFORM_APPROVED);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultPlatformApproved);
        }

        @Test
        @DisplayName("PENDING_PUBLIC_REVIEW → PLATFORM_REJECTED")
        void shouldPlatformReject() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformReject();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PLATFORM_REJECTED);
        }

        @Test
        @DisplayName("PLATFORM_APPROVED → PUBLIC")
        void shouldMakePublic() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            r.makePublic();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PUBLIC);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultMadePublic);
        }

        @Test
        @DisplayName("PUBLIC → ANOMALY_PENDING")
        void shouldMarkAnomaly() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            r.makePublic();
            r.markAnomaly();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.ANOMALY_PENDING);
        }

        @Test
        @DisplayName("ANOMALY_PENDING → PUBLIC (resolve)")
        void shouldResolveAnomaly() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            r.makePublic();
            r.markAnomaly();
            r.resolveAnomaly();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PUBLIC);
        }

        @Test
        @DisplayName("PLATFORM_REJECTED → NOT_SUBMITTED")
        void shouldReturnToNotSubmittedFromRejected() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformReject();
            r.returnToNotSubmitted();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.NOT_SUBMITTED);
        }

        @Test
        @DisplayName("PUBLIC → PLATFORM_TAKEDOWN")
        void shouldPlatformTakedown() {
            var r = createInternalPublished();
            r.submitForReview();
            r.platformApprove();
            r.makePublic();
            r.platformTakedown();
            assertThat(r.publicStatus()).isEqualTo(ResultPublicStatus.PLATFORM_TAKEDOWN);
            assertThat(r.domainEvents()).anyMatch(e -> e instanceof ResultPlatformTakenDown);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("INTERNAL_PUBLISHED → DRAFT rejected")
        void shouldRejectDirectReturnToDraftFromPublished() {
            var r = createInternalPublished();
            assertThatThrownBy(r::returnToDraft)
                    .isInstanceOf(InvalidResultStateTransitionException.class);
        }

        @Test
        @DisplayName("DRAFT → INTERNAL_WITHDRAWN rejected")
        void shouldRejectWithdrawFromDraft() {
            var r = createDraft();
            assertThatThrownBy(r::withdrawInternal)
                    .isInstanceOf(InvalidResultStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot make public before platform approval")
        void shouldRejectMakePublicBeforeApproval() {
            var r = createInternalPublished();
            r.submitForReview();
            assertThatThrownBy(r::makePublic)
                    .isInstanceOf(InvalidResultStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot submit for review twice")
        void shouldRejectSubmitForReviewTwice() {
            var r = createInternalPublished();
            r.submitForReview();
            assertThatThrownBy(r::submitForReview)
                    .isInstanceOf(InvalidResultStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var r = createDraft();
            assertThatThrownBy(() -> r.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
