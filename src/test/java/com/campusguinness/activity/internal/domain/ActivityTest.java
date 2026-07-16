package com.campusguinness.activity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Activity aggregate")
class ActivityTest {

    private Activity.Builder validBuilder() {
        return new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .title("校园数学挑战赛")
                .description("面向全校的数学竞赛")
                .startTime(Instant.parse("2026-09-01T08:00:00Z"))
                .endTime(Instant.parse("2026-09-02T17:00:00Z"))
                .location("学校体育馆")
                .createdBy(UUID.randomUUID());
    }

    private Activity createDraft() {
        return Activity.create(validBuilder());
    }

    private Activity createPublished() {
        var a = createDraft();
        a.publish();
        return a;
    }

    private Activity createInProgress() {
        var a = createPublished();
        a.beginExecution();
        return a;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("CG-ACT-001: creates in DRAFT + NOT_SUBMITTED")
        void shouldCreateInDraftAndNotSubmitted() {
            var a = createDraft();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.DRAFT);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
        }

        @Test
        @DisplayName("null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> Activity.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> Activity.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null createdBy rejected")
        void shouldRejectNullCreatedBy() {
            assertThatThrownBy(() -> Activity.create(validBuilder().createdBy(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-017: null title rejected")
        void shouldRejectNullTitle() {
            assertThatThrownBy(() -> Activity.create(validBuilder().title(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-017: title over 200 chars rejected")
        void shouldRejectTooLongTitle() {
            assertThatThrownBy(() -> Activity.create(validBuilder().title("A".repeat(201))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Field mutation (DRAFT only)")
    class FieldMutation {

        @Test
        @DisplayName("updateTitle allowed in DRAFT")
        void shouldAllowUpdateTitleInDraft() {
            var a = createDraft();
            a.updateTitle("新标题");
            assertThat(a.title()).isEqualTo("新标题");
        }

        @Test
        @DisplayName("updateTitle rejected after publish")
        void shouldRejectUpdateTitleAfterPublish() {
            var a = createPublished();
            assertThatThrownBy(() -> a.updateTitle("新标题"))
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("updateTimeRange allowed in DRAFT")
        void shouldAllowUpdateTimeRangeInDraft() {
            var a = createDraft();
            Instant start = Instant.parse("2026-10-01T08:00:00Z");
            Instant end = Instant.parse("2026-10-02T17:00:00Z");
            a.updateTimeRange(start, end);
            assertThat(a.startTime()).isEqualTo(start);
            assertThat(a.endTime()).isEqualTo(end);
        }

        @Test
        @DisplayName("updateTimeRange rejects end before start")
        void shouldRejectEndBeforeStart() {
            var a = createDraft();
            Instant start = Instant.parse("2026-10-02T08:00:00Z");
            Instant end = Instant.parse("2026-10-01T08:00:00Z");
            assertThatThrownBy(() -> a.updateTimeRange(start, end))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("updateLocation allowed in DRAFT")
        void shouldAllowUpdateLocationInDraft() {
            var a = createDraft();
            a.updateLocation("新地点");
            assertThat(a.location()).isEqualTo("新地点");
        }
    }

    @Nested
    @DisplayName("execution_status transitions")
    class ExecutionTransitions {

        @Test
        @DisplayName("CG-ACT-002: DRAFT → PUBLISHED")
        void shouldPublishFromDraft() {
            var a = createDraft();
            a.publish();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.PUBLISHED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityPublished);
        }

        @Test
        @DisplayName("CG-ACT-003: PUBLISHED → IN_PROGRESS")
        void shouldBeginExecutionFromPublished() {
            var a = createPublished();
            a.beginExecution();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityExecutionStarted);
        }

        @Test
        @DisplayName("CG-ACT-004: IN_PROGRESS → ENDED (terminal)")
        void shouldEndFromInProgress() {
            var a = createInProgress();
            a.end();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.ENDED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityEnded);
        }

        @Test
        @DisplayName("CG-ACT-005: DRAFT → CANCELLED (terminal, resets public)")
        void shouldCancelFromDraft() {
            var a = createDraft();
            a.cancel();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.CANCELLED);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityCancelled);
        }

        @Test
        @DisplayName("CG-ACT-006: PUBLISHED → CANCELLED (terminal, resets public)")
        void shouldCancelFromPublished() {
            var a = createPublished();
            a.cancel();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.CANCELLED);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityCancelled);
        }

        @Test
        @DisplayName("cancel resets public_status when activity was public")
        void cancelResetsPublicStatus() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            a.makePublic();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PUBLIC);
            // Cancel from PUBLISHED — but we just made it public, still PUBLISHED execution
            a.cancel();
            assertThat(a.executionStatus()).isEqualTo(ExecutionStatus.CANCELLED);
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("public_status transitions")
    class PublicTransitions {

        @Test
        @DisplayName("CG-ACT-007: NOT_SUBMITTED → PENDING_PLATFORM_REVIEW (from PUBLISHED)")
        void shouldSubmitForReviewFromPublished() {
            var a = createPublished();
            a.submitForReview();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PENDING_PLATFORM_REVIEW);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivitySubmittedForReview);
        }

        @Test
        @DisplayName("CG-ACT-007: can submit for review from IN_PROGRESS")
        void shouldSubmitForReviewFromInProgress() {
            var a = createInProgress();
            a.submitForReview();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PENDING_PLATFORM_REVIEW);
        }

        @Test
        @DisplayName("CG-ACT-007: can submit for review from ENDED")
        void shouldSubmitForReviewFromEnded() {
            var a = createInProgress();
            a.end();
            a.submitForReview();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PENDING_PLATFORM_REVIEW);
        }

        @Test
        @DisplayName("CG-ACT-008: PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED")
        void shouldPlatformApprove() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PLATFORM_APPROVED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityPlatformApproved);
        }

        @Test
        @DisplayName("CG-ACT-009: PENDING_PLATFORM_REVIEW → PLATFORM_REJECTED")
        void shouldPlatformReject() {
            var a = createPublished();
            a.submitForReview();
            a.platformReject("内容不符合平台标准");
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PLATFORM_REJECTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityPlatformRejected);
        }

        @Test
        @DisplayName("CG-ACT-009: platform reject requires reason")
        void shouldRequireReasonOnPlatformReject() {
            var a = createPublished();
            a.submitForReview();
            assertThatThrownBy(() -> a.platformReject(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-010: PLATFORM_APPROVED → PUBLIC")
        void shouldMakePublic() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            a.makePublic();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PUBLIC);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityMadePublic);
        }

        @Test
        @DisplayName("CG-ACT-011: PUBLIC → SCHOOL_WITHDRAWN")
        void shouldSchoolWithdraw() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            a.makePublic();
            a.schoolWithdraw();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.SCHOOL_WITHDRAWN);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityWithdrawnBySchool);
        }

        @Test
        @DisplayName("CG-ACT-012: PUBLIC → PLATFORM_TAKEDOWN")
        void shouldPlatformTakedown() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            a.makePublic();
            a.platformTakedown("违规内容");
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.PLATFORM_TAKEDOWN);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityTakenDownByPlatform);
        }

        @Test
        @DisplayName("CG-ACT-013: PLATFORM_REJECTED → NOT_SUBMITTED")
        void shouldReturnToNotSubmittedFromRejected() {
            var a = createPublished();
            a.submitForReview();
            a.platformReject("reason");
            a.returnToNotSubmitted();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ActivityPublicReviewReset);
        }

        @Test
        @DisplayName("CG-ACT-013: SCHOOL_WITHDRAWN → NOT_SUBMITTED")
        void shouldReturnToNotSubmittedFromWithdrawn() {
            var a = createPublished();
            a.submitForReview();
            a.platformApprove();
            a.makePublic();
            a.schoolWithdraw();
            a.returnToNotSubmitted();
            assertThat(a.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);
        }
    }

    @Nested
    @DisplayName("Cross-machine rules")
    class CrossMachineRules {

        @Test
        @DisplayName("CG-ACT-014: DRAFT cannot submit for public review")
        void shouldRejectSubmitForReviewFromDraft() {
            var a = createDraft();
            assertThatThrownBy(a::submitForReview)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-015: CANCELLED cannot submit for public review")
        void shouldRejectSubmitForReviewFromCancelled() {
            var a = createDraft();
            a.cancel();
            assertThatThrownBy(a::submitForReview)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Illegal execution transitions")
    class IllegalExecutionTransitions {

        @Test
        @DisplayName("CG-ACT-019: ENDED → any rejected (terminal)")
        void shouldRejectTransitionFromEnded() {
            var a = createInProgress();
            a.end();
            assertThatThrownBy(a::publish)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
            assertThatThrownBy(a::beginExecution)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
            assertThatThrownBy(a::cancel)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-020: CANCELLED → any rejected (terminal)")
        void shouldRejectTransitionFromCancelled() {
            var a = createDraft();
            a.cancel();
            assertThatThrownBy(a::publish)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
            assertThatThrownBy(a::beginExecution)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
            assertThatThrownBy(a::end)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
            assertThatThrownBy(a::cancel)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("IN_PROGRESS → cancel rejected")
        void shouldRejectCancelFromInProgress() {
            var a = createInProgress();
            assertThatThrownBy(a::cancel)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("PUBLISHED → end rejected")
        void shouldRejectEndFromPublished() {
            var a = createPublished();
            assertThatThrownBy(a::end)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("DRAFT → beginExecution rejected")
        void shouldRejectBeginExecutionFromDraft() {
            var a = createDraft();
            assertThatThrownBy(a::beginExecution)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Illegal public transitions")
    class IllegalPublicTransitions {

        @Test
        @DisplayName("Cannot submit for review twice")
        void shouldRejectSubmitForReviewTwice() {
            var a = createPublished();
            a.submitForReview();
            assertThatThrownBy(a::submitForReview)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot make public before platform approval")
        void shouldRejectMakePublicBeforeApproval() {
            var a = createPublished();
            a.submitForReview();
            assertThatThrownBy(a::makePublic)
                    .isInstanceOf(InvalidActivityStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var a = createDraft();
            assertThatThrownBy(() -> a.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
