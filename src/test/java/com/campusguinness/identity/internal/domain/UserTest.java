package com.campusguinness.identity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User aggregate")
class UserTest {

    private final Instant startedAt = Instant.parse("2026-08-06T01:00:00Z");

    private User.Builder validBuilder() {
        return new User.Builder().id(new UserId(UUID.randomUUID())).username("testuser");
    }

    private User createPending() { return User.create(validBuilder()); }
    private User createNormal() { var u = createPending(); u.activate(); return u; }

    @Nested @DisplayName("Creation")
    class Creation {
        @Test @DisplayName("creates in PENDING_ACTIVATION status")
        void shouldCreateInPendingActivation() {
            assertThat(createPending().status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        }

        @Test @DisplayName("null username rejected")
        void shouldRejectNullUsername() {
            assertThatThrownBy(() -> User.create(validBuilder().username(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("username over 100 chars rejected")
        void shouldRejectTooLongUsername() {
            assertThatThrownBy(() -> User.create(validBuilder().username("A".repeat(101))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("starts with empty memberships")
        void shouldStartWithNoMemberships() {
            assertThat(createPending().memberships()).isEmpty();
        }
    }

    @Nested @DisplayName("Account state transitions")
    class StateTransitions {
        @Test @DisplayName("PENDING_ACTIVATION -> NORMAL")
        void shouldActivate() {
            var u = createPending();
            u.activate();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
            assertThat(u.domainEvents()).anyMatch(e -> e instanceof UserActivated);
        }

        @Test @DisplayName("NORMAL -> LOCKED")
        void shouldLock() {
            var u = createNormal();
            u.lock();
            assertThat(u.status()).isEqualTo(AccountStatus.LOCKED);
        }

        @Test @DisplayName("LOCKED -> NORMAL")
        void shouldUnlock() {
            var u = createNormal();
            u.lock();
            u.unlock();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
        }

        @Test @DisplayName("NORMAL -> DISABLED")
        void shouldDisable() {
            var u = createNormal();
            u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
            assertThat(u.domainEvents()).anyMatch(e -> e instanceof UserDisabled);
        }

        @Test @DisplayName("PENDING_ACTIVATION -> DISABLED")
        void shouldDisableFromPending() {
            var u = createPending();
            u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
        }

        @Test @DisplayName("LOCKED -> DISABLED")
        void shouldDisableFromLocked() {
            var u = createNormal();
            u.lock();
            u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
        }

        @Test @DisplayName("DISABLED -> NORMAL")
        void shouldReEnable() {
            var u = createNormal();
            u.disable();
            u.reEnable();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
        }
    }

    @Nested @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test @DisplayName("PENDING_ACTIVATION -> LOCKED rejected")
        void shouldRejectLockFromPending() {
            assertThatThrownBy(() -> createPending().lock())
                    .isInstanceOf(InvalidAccountStateTransitionException.class);
        }

        @Test @DisplayName("DISABLED -> DISABLED rejected")
        void shouldRejectDoubleDisable() {
            var u = createNormal();
            u.disable();
            assertThatThrownBy(u::disable).isInstanceOf(InvalidAccountStateTransitionException.class);
        }

        @Test @DisplayName("NORMAL -> PENDING_ACTIVATION rejected")
        void shouldRejectActivateOnNormal() {
            assertThatThrownBy(() -> createNormal().activate())
                    .isInstanceOf(InvalidAccountStateTransitionException.class);
        }
    }

    @Nested @DisplayName("School memberships")
    class Memberships {
        @Test @DisplayName("NORMAL user can receive STUDENT membership")
        void normalUserCanReceiveStudentMembership() {
            var u = createNormal();
            var membership = u.grantStudentMembership(id(), UUID.randomUUID(), startedAt);

            assertThat(membership.role()).isEqualTo(SchoolRole.STUDENT);
            assertThat(u.activeMemberships()).containsExactly(membership);
        }

        @Test @DisplayName("NORMAL user can receive SCHOOL_ADMIN membership")
        void normalUserCanReceiveSchoolAdminMembership() {
            var u = createNormal();
            var membership = u.grantSchoolAdminMembership(id(), UUID.randomUUID(), startedAt);

            assertThat(membership.role()).isEqualTo(SchoolRole.SCHOOL_ADMIN);
            assertThat(u.activeMemberships()).containsExactly(membership);
        }

        @Test @DisplayName("PENDING_ACTIVATION user cannot receive membership")
        void pendingUserCannotReceiveMembership() {
            assertThatThrownBy(() -> createPending().grantStudentMembership(id(), UUID.randomUUID(), startedAt))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test @DisplayName("LOCKED user cannot receive membership")
        void lockedUserCannotReceiveMembership() {
            var u = createNormal();
            u.lock();

            assertThatThrownBy(() -> u.grantStudentMembership(id(), UUID.randomUUID(), startedAt))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test @DisplayName("DISABLED user cannot receive membership")
        void disabledUserCannotReceiveMembership() {
            var u = createNormal();
            u.disable();

            assertThatThrownBy(() -> u.grantStudentMembership(id(), UUID.randomUUID(), startedAt))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test @DisplayName("same school cannot have two ACTIVE memberships")
        void rejectsDuplicateActiveMembershipForSameSchool() {
            var u = createNormal();
            UUID schoolId = UUID.randomUUID();
            u.grantStudentMembership(id(), schoolId, startedAt);

            assertThatThrownBy(() -> u.grantSchoolAdminMembership(id(), schoolId, startedAt.plusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test @DisplayName("same school can receive new ACTIVE membership after ENDED history")
        void allowsNewActiveAfterEndedHistory() {
            var u = createNormal();
            UUID schoolId = UUID.randomUUID();
            u.grantStudentMembership(id(), schoolId, startedAt);
            u.endMembership(schoolId, startedAt.plusSeconds(60));

            var next = u.grantSchoolAdminMembership(id(), schoolId, startedAt.plusSeconds(120));

            assertThat(u.activeMembershipFor(schoolId)).contains(next);
            assertThat(u.membershipHistoryFor(schoolId)).hasSize(2);
        }

        @Test @DisplayName("different schools can be ACTIVE at the same time")
        void allowsDifferentActiveSchools() {
            var u = createNormal();

            u.grantStudentMembership(id(), UUID.randomUUID(), startedAt);
            u.grantSchoolAdminMembership(id(), UUID.randomUUID(), startedAt);

            assertThat(u.activeMemberships()).hasSize(2);
        }

        @Test @DisplayName("ending membership preserves history")
        void endingMembershipPreservesHistory() {
            var u = createNormal();
            UUID schoolId = UUID.randomUUID();
            u.grantStudentMembership(id(), schoolId, startedAt);

            u.endMembership(schoolId, startedAt.plusSeconds(60));

            assertThat(u.activeMemberships()).isEmpty();
            assertThat(u.membershipHistoryFor(schoolId)).hasSize(1);
            assertThat(u.membershipHistoryFor(schoolId).getFirst().status()).isEqualTo(MembershipStatus.ENDED);
        }

        @Test @DisplayName("activeMembershipFor only returns ACTIVE membership")
        void activeMembershipForOnlyReturnsActive() {
            var u = createNormal();
            UUID schoolId = UUID.randomUUID();
            u.grantStudentMembership(id(), schoolId, startedAt);
            u.endMembership(schoolId, startedAt.plusSeconds(60));

            assertThat(u.activeMembershipFor(schoolId)).isEmpty();
        }

        @Test @DisplayName("membership collections are unmodifiable")
        void membershipCollectionsAreUnmodifiable() {
            var u = createNormal();
            u.grantStudentMembership(id(), UUID.randomUUID(), startedAt);

            assertThatThrownBy(() -> u.memberships().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> u.activeMemberships().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test @DisplayName("reconstituted duplicate active school is rejected")
        void reconstitutedDuplicateActiveSchoolIsRejected() {
            UUID schoolId = UUID.randomUUID();

            assertThatThrownBy(() -> User.reconstitute(
                    validBuilder(),
                    AccountStatus.NORMAL,
                    List.of(active(schoolId), active(schoolId))
            )).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested @DisplayName("Collection protection")
    class CollectionProtection {
        @Test @DisplayName("domain events unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createPending().domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private SchoolMembershipId id() {
        return new SchoolMembershipId(UUID.randomUUID());
    }

    private SchoolMembership active(UUID schoolId) {
        return SchoolMembership.reconstitute(
                id(),
                schoolId,
                SchoolRole.STUDENT,
                MembershipStatus.ACTIVE,
                startedAt,
                null,
                1
        );
    }
}
