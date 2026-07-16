package com.campusguinness.activity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActivityApplication aggregate")
class ActivityApplicationTest {

    private ActivityApplication.Builder validBuilder() {
        return new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .applicantId(UUID.randomUUID())
                .title("校园数学挑战赛")
                .description("面向全校的数学竞赛活动");
    }

    private ActivityApplication createDraft() {
        return ActivityApplication.create(validBuilder());
    }

    private ActivityApplication createSubmitted() {
        var app = createDraft();
        app.submit();
        return app;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("CG-ACT-APP-001: creates in DRAFT status")
        void shouldCreateInDraftStatus() {
            var app = createDraft();
            assertThat(app.status()).isEqualTo(ApplicationStatus.DRAFT);
        }

        @Test
        @DisplayName("CG-ACT-APP-014: applicationVersion starts at 1")
        void shouldStartAtVersionOne() {
            var app = createDraft();
            assertThat(app.applicationVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("CG-ACT-APP-011: null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-012: null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-012: null applicantId rejected")
        void shouldRejectNullApplicantId() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().applicantId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-011: null title rejected")
        void shouldRejectNullTitle() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().title(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-011: blank title rejected")
        void shouldRejectBlankTitle() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().title("  ")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-011: title over 200 chars rejected")
        void shouldRejectTooLongTitle() {
            assertThatThrownBy(() -> ActivityApplication.create(validBuilder().title("A".repeat(201))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Field mutation (DRAFT only)")
    class FieldMutation {

        @Test
        @DisplayName("CG-ACT-APP-016: updateTitle allowed in DRAFT")
        void shouldAllowUpdateTitleInDraft() {
            var app = createDraft();
            app.updateTitle("新标题");
            assertThat(app.title()).isEqualTo("新标题");
        }

        @Test
        @DisplayName("CG-ACT-APP-016: updateTitle rejected in SUBMITTED")
        void shouldRejectUpdateTitleInSubmitted() {
            var app = createSubmitted();
            assertThatThrownBy(() -> app.updateTitle("新标题"))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-016: updateDescription allowed in DRAFT")
        void shouldAllowUpdateDescriptionInDraft() {
            var app = createDraft();
            app.updateDescription("新的描述");
            assertThat(app.description()).isEqualTo("新的描述");
        }

        @Test
        @DisplayName("CG-ACT-APP-016: updateDescription rejected in SUBMITTED")
        void shouldRejectUpdateDescriptionInSubmitted() {
            var app = createSubmitted();
            assertThatThrownBy(() -> app.updateDescription("new"))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("CG-ACT-APP-001: DRAFT → SUBMITTED")
        void shouldSubmitFromDraft() {
            var app = createDraft();
            app.submit();
            assertThat(app.status()).isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(app.domainEvents()).anyMatch(e -> e instanceof ActivityApplicationSubmitted);
        }

        @Test
        @DisplayName("CG-ACT-APP-002: SUBMITTED → APPROVED")
        void shouldApproveFromSubmitted() {
            var app = createSubmitted();
            UUID activityId = UUID.randomUUID();
            app.approve(UUID.randomUUID(), activityId);
            assertThat(app.status()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(app.createdActivityId()).isEqualTo(activityId);
            assertThat(app.reviewedBy()).isNotNull();
            assertThat(app.reviewedAt()).isNotNull();
            assertThat(app.domainEvents()).anyMatch(e -> e instanceof ActivityApplicationApproved);
        }

        @Test
        @DisplayName("CG-ACT-APP-002: approve requires activityId")
        void shouldRequireActivityIdOnApprove() {
            var app = createSubmitted();
            assertThatThrownBy(() -> app.approve(UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-003: SUBMITTED → REJECTED")
        void shouldRejectFromSubmitted() {
            var app = createSubmitted();
            app.reject(UUID.randomUUID(), "活动方案需要完善");
            assertThat(app.status()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(app.rejectReason()).isEqualTo("活动方案需要完善");
            assertThat(app.domainEvents()).anyMatch(e -> e instanceof ActivityApplicationRejected);
        }

        @Test
        @DisplayName("CG-ACT-APP-013: reject requires reason")
        void shouldRequireReasonOnReject() {
            var app = createSubmitted();
            assertThatThrownBy(() -> app.reject(UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> app.reject(UUID.randomUUID(), "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-004: SUBMITTED → WITHDRAWN")
        void shouldWithdrawFromSubmitted() {
            var app = createSubmitted();
            app.withdraw();
            assertThat(app.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
            assertThat(app.domainEvents()).anyMatch(e -> e instanceof ActivityApplicationWithdrawn);
        }

        @Test
        @DisplayName("CG-ACT-APP-005: REJECTED → DRAFT (version increments)")
        void shouldReturnToDraftFromRejected() {
            var app = createSubmitted();
            app.reject(UUID.randomUUID(), "需要修改");
            app.returnToDraft();
            assertThat(app.status()).isEqualTo(ApplicationStatus.DRAFT);
            assertThat(app.applicationVersion()).isEqualTo(2);
            assertThat(app.domainEvents()).anyMatch(e -> e instanceof ActivityApplicationReturnedToDraft);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("CG-ACT-APP-006: DRAFT → APPROVED rejected")
        void shouldRejectDirectApproveFromDraft() {
            var app = createDraft();
            assertThatThrownBy(() -> app.approve(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-007: DRAFT → REJECTED rejected")
        void shouldRejectDirectRejectFromDraft() {
            var app = createDraft();
            assertThatThrownBy(() -> app.reject(UUID.randomUUID(), "reason"))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-008: APPROVED → any rejected")
        void shouldRejectTransitionFromApproved() {
            var app = createSubmitted();
            app.approve(UUID.randomUUID(), UUID.randomUUID());
            assertThatThrownBy(app::submit)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
            assertThatThrownBy(() -> app.approve(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
            assertThatThrownBy(app::withdraw)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-009: WITHDRAWN → any rejected")
        void shouldRejectTransitionFromWithdrawn() {
            var app = createSubmitted();
            app.withdraw();
            assertThatThrownBy(app::submit)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
            assertThatThrownBy(() -> app.approve(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
            assertThatThrownBy(app::withdraw)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-ACT-APP-010: REJECTED → SUBMITTED rejected (must go via DRAFT)")
        void shouldRejectDirectSubmitFromRejected() {
            var app = createSubmitted();
            app.reject(UUID.randomUUID(), "reason");
            assertThatThrownBy(app::submit)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }

        @Test
        @DisplayName("SUBMITTED → DRAFT rejected (no direct revert)")
        void shouldRejectReturnToDraftFromSubmitted() {
            var app = createSubmitted();
            assertThatThrownBy(app::returnToDraft)
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var app = createDraft();
            assertThatThrownBy(() -> app.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
