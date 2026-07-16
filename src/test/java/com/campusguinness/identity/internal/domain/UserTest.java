package com.campusguinness.identity.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("User aggregate")
class UserTest {

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
        @Test @DisplayName("PENDING_ACTIVATION → NORMAL")
        void shouldActivate() {
            var u = createPending(); u.activate();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
            assertThat(u.domainEvents()).anyMatch(e -> e instanceof UserActivated);
        }
        @Test @DisplayName("NORMAL → LOCKED")
        void shouldLock() {
            var u = createNormal(); u.lock();
            assertThat(u.status()).isEqualTo(AccountStatus.LOCKED);
        }
        @Test @DisplayName("LOCKED → NORMAL")
        void shouldUnlock() {
            var u = createNormal(); u.lock(); u.unlock();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
        }
        @Test @DisplayName("NORMAL → DISABLED")
        void shouldDisable() {
            var u = createNormal(); u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
            assertThat(u.domainEvents()).anyMatch(e -> e instanceof UserDisabled);
        }
        @Test @DisplayName("PENDING_ACTIVATION → DISABLED")
        void shouldDisableFromPending() {
            var u = createPending(); u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
        }
        @Test @DisplayName("LOCKED → DISABLED")
        void shouldDisableFromLocked() {
            var u = createNormal(); u.lock(); u.disable();
            assertThat(u.status()).isEqualTo(AccountStatus.DISABLED);
        }
        @Test @DisplayName("DISABLED → NORMAL")
        void shouldReEnable() {
            var u = createNormal(); u.disable(); u.reEnable();
            assertThat(u.status()).isEqualTo(AccountStatus.NORMAL);
        }
    }

    @Nested @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test @DisplayName("PENDING_ACTIVATION → LOCKED rejected")
        void shouldRejectLockFromPending() {
            assertThatThrownBy(() -> createPending().lock())
                    .isInstanceOf(InvalidAccountStateTransitionException.class);
        }
        @Test @DisplayName("DISABLED → DISABLED rejected")
        void shouldRejectDoubleDisable() {
            var u = createNormal(); u.disable();
            assertThatThrownBy(u::disable).isInstanceOf(InvalidAccountStateTransitionException.class);
        }
        @Test @DisplayName("NORMAL → PENDING_ACTIVATION rejected")
        void shouldRejectActivateOnNormal() {
            assertThatThrownBy(() -> createNormal().activate())
                    .isInstanceOf(InvalidAccountStateTransitionException.class);
        }
    }

    @Nested @DisplayName("School memberships")
    class Memberships {
        @Test @DisplayName("add active membership")
        void shouldAddMembership() {
            var u = createPending();
            var m = new SchoolMembership(UUID.randomUUID(), "TEACHER", MembershipStatus.ACTIVE);
            u.addMembership(m);
            assertThat(u.memberships()).hasSize(1);
            assertThat(u.activeMemberships()).hasSize(1);
        }
        @Test @DisplayName("reject duplicate active membership for same school")
        void shouldRejectDuplicateMembership() {
            var u = createPending();
            UUID sid = UUID.randomUUID();
            u.addMembership(new SchoolMembership(sid, "TEACHER", MembershipStatus.ACTIVE));
            assertThatThrownBy(() -> u.addMembership(new SchoolMembership(sid, "STUDENT", MembershipStatus.ACTIVE)))
                    .isInstanceOf(IllegalStateException.class);
        }
        @Test @DisplayName("end membership")
        void shouldEndMembership() {
            var u = createPending();
            UUID sid = UUID.randomUUID();
            u.addMembership(new SchoolMembership(sid, "TEACHER", MembershipStatus.ACTIVE));
            u.endMembership(sid);
            assertThat(u.activeMemberships()).isEmpty();
        }
        @Test @DisplayName("end non-existent membership throws")
        void shouldRejectEndNonExistent() {
            assertThatThrownBy(() -> createPending().endMembership(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested @DisplayName("Collection protection")
    class CollectionProtection {
        @Test @DisplayName("memberships unmodifiable")
        void membershipsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createPending().memberships().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        @Test @DisplayName("domain events unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createPending().domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
