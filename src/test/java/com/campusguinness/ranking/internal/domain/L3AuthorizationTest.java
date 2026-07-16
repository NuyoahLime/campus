package com.campusguinness.ranking.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("L3Authorization aggregate")
class L3AuthorizationTest {

    private L3Authorization.Builder validBuilder() {
        return new L3Authorization.Builder()
                .id(new L3AuthorizationId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .ruleVersionId(UUID.randomUUID())
                .allowSchoolName(true)
                .allowStudentName(false);
    }

    private L3Authorization createDraft() { return L3Authorization.create(validBuilder()); }

    private L3Authorization createSubmitted() {
        var a = createDraft(); a.submit(); return a;
    }

    private L3Authorization createApproved() {
        var a = createSubmitted(); a.approve(UUID.randomUUID(), "ok"); return a;
    }

    @Nested @DisplayName("Creation")
    class Creation {
        @Test @DisplayName("creates in DRAFT status")
        void shouldCreateInDraft() {
            assertThat(createDraft().status()).isEqualTo(AuthorizationStatus.DRAFT);
        }
        @Test @DisplayName("null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> L3Authorization.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        @Test @DisplayName("null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> L3Authorization.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("State transitions")
    class StateTransitions {
        @Test @DisplayName("DRAFT → PENDING_REVIEW")
        void shouldSubmit() {
            var a = createDraft(); a.submit();
            assertThat(a.status()).isEqualTo(AuthorizationStatus.PENDING_REVIEW);
            assertThat(a.submittedAt()).isNotNull();
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationSubmitted);
        }
        @Test @DisplayName("PENDING_REVIEW → APPROVED")
        void shouldApprove() {
            var a = createSubmitted(); a.approve(UUID.randomUUID(), "approved");
            assertThat(a.status()).isEqualTo(AuthorizationStatus.APPROVED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationApproved);
        }
        @Test @DisplayName("PENDING_REVIEW → REJECTED")
        void shouldReject() {
            var a = createSubmitted(); a.reject(UUID.randomUUID(), "incomplete");
            assertThat(a.status()).isEqualTo(AuthorizationStatus.REJECTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationRejected);
        }
        @Test @DisplayName("reject requires reason")
        void shouldRequireReasonOnReject() {
            assertThatThrownBy(() -> createSubmitted().reject(UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        @Test @DisplayName("REJECTED → DRAFT")
        void shouldReturnToDraft() {
            var a = createSubmitted(); a.reject(UUID.randomUUID(), "reason"); a.returnToDraft();
            assertThat(a.status()).isEqualTo(AuthorizationStatus.DRAFT);
            assertThat(a.submittedAt()).isNull();
        }
        @Test @DisplayName("APPROVED → SUSPENDED (school paused)")
        void shouldSuspend() {
            var a = createApproved(); a.suspend();
            assertThat(a.status()).isEqualTo(AuthorizationStatus.SUSPENDED);
            assertThat(a.pausedAt()).isNotNull();
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationSuspended);
        }
        @Test @DisplayName("SUSPENDED → APPROVED (resume)")
        void shouldResume() {
            var a = createApproved(); a.suspend(); a.resume();
            assertThat(a.status()).isEqualTo(AuthorizationStatus.APPROVED);
            assertThat(a.pausedAt()).isNull();
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationResumed);
        }
        @Test @DisplayName("DRAFT → WITHDRAWN (terminal)")
        void shouldWithdrawFromDraft() {
            var a = createDraft(); a.withdraw("no longer needed");
            assertThat(a.status()).isEqualTo(AuthorizationStatus.WITHDRAWN);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof L3AuthorizationWithdrawn);
        }
        @Test @DisplayName("APPROVED → WITHDRAWN (terminal)")
        void shouldWithdrawFromApproved() {
            var a = createApproved(); a.withdraw("school disabled");
            assertThat(a.status()).isEqualTo(AuthorizationStatus.WITHDRAWN);
        }
        @Test @DisplayName("SUSPENDED → WITHDRAWN (terminal)")
        void shouldWithdrawFromSuspended() {
            var a = createApproved(); a.suspend(); a.withdraw("school disabled");
            assertThat(a.status()).isEqualTo(AuthorizationStatus.WITHDRAWN);
        }
        @Test @DisplayName("isUsable true only when APPROVED")
        void shouldBeUsableOnlyWhenApproved() {
            assertThat(createDraft().isUsable()).isFalse();
            assertThat(createSubmitted().isUsable()).isFalse();
            assertThat(createApproved().isUsable()).isTrue();
            var s = createApproved(); s.suspend();
            assertThat(s.isUsable()).isFalse();
        }
    }

    @Nested @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test @DisplayName("WITHDRAWN → any rejected (terminal)")
        void shouldRejectFromWithdrawn() {
            var a = createDraft(); a.withdraw("reason");
            assertThatThrownBy(a::submit).isInstanceOf(InvalidAuthorizationStateTransitionException.class);
            assertThatThrownBy(() -> a.approve(UUID.randomUUID(), "x")).isInstanceOf(InvalidAuthorizationStateTransitionException.class);
            assertThatThrownBy(a::suspend).isInstanceOf(InvalidAuthorizationStateTransitionException.class);
            assertThatThrownBy(a::resume).isInstanceOf(InvalidAuthorizationStateTransitionException.class);
        }
        @Test @DisplayName("DRAFT → APPROVED rejected")
        void shouldRejectApproveFromDraft() {
            assertThatThrownBy(() -> createDraft().approve(UUID.randomUUID(), "x"))
                    .isInstanceOf(InvalidAuthorizationStateTransitionException.class);
        }
        @Test @DisplayName("APPROVED → REJECTED rejected")
        void shouldRejectRejectFromApproved() {
            assertThatThrownBy(() -> createApproved().reject(UUID.randomUUID(), "x"))
                    .isInstanceOf(InvalidAuthorizationStateTransitionException.class);
        }
    }

    @Nested @DisplayName("Collection protection")
    class CollectionProtection {
        @Test @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createDraft().domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
