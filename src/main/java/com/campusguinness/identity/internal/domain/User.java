package com.campusguinness.identity.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User aggregate root — final aggregate (13/13).
 *
 * <p>Account state machine (4 states):
 * <pre>
 *   PENDING_ACTIVATION → NORMAL ⇄ LOCKED
 *          ↓               ↓        ↓
 *        DISABLED ←────────┴────────┘
 *          ↓
 *        NORMAL
 * </pre>
 *
 * <p>SchoolMembership: USER_AGGREGATE_CHILD_ENTITY — simple ACTIVE/ENDED record
 * referencing School by ID. No independent state machine. Not deferred.
 *
 * <p>Excluded from domain (authentication infrastructure):
 * password_hash, login_failures, locked_until.
 */
public final class User {

    private final UserId id;
    private final String username;
    private AccountStatus status;
    private final String platformRole;
    private final List<SchoolMembership> memberships;
    private final List<Object> domainEvents;

    private User(Builder b, AccountStatus status) {
        this.id = b.id; this.username = b.username; this.status = status;
        this.platformRole = b.platformRole;
        this.memberships = new ArrayList<>(); this.domainEvents = new ArrayList<>();
    }

    public static User create(Builder builder) {
        validate(builder);
        return new User(builder, AccountStatus.PENDING_ACTIVATION);
    }

    public static User reconstitute(Builder builder, AccountStatus status,
            List<SchoolMembership> memberships) {
        validate(builder);
        var u = new User(builder, status);
        u.memberships.addAll(memberships);
        return u;
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.username == null || b.username.isBlank()) throw new IllegalArgumentException("username required");
        if (b.username.length() > 100) throw new IllegalArgumentException("username max 100 chars");
    }

    // ── Account state transitions ──

    /** PENDING_ACTIVATION → NORMAL */
    public void activate() {
        if (status != AccountStatus.PENDING_ACTIVATION) {
            throw new InvalidAccountStateTransitionException(status, "activate");
        }
        this.status = AccountStatus.NORMAL;
        domainEvents.add(new UserActivated(id));
    }

    /** NORMAL → LOCKED */
    public void lock() {
        if (status != AccountStatus.NORMAL) {
            throw new InvalidAccountStateTransitionException(status, "lock");
        }
        this.status = AccountStatus.LOCKED;
    }

    /** LOCKED → NORMAL */
    public void unlock() {
        if (status != AccountStatus.LOCKED) {
            throw new InvalidAccountStateTransitionException(status, "unlock");
        }
        this.status = AccountStatus.NORMAL;
    }

    /** PENDING_ACTIVATION / NORMAL / LOCKED → DISABLED */
    public void disable() {
        if (status != AccountStatus.PENDING_ACTIVATION
                && status != AccountStatus.NORMAL
                && status != AccountStatus.LOCKED) {
            throw new InvalidAccountStateTransitionException(status, "disable");
        }
        this.status = AccountStatus.DISABLED;
        domainEvents.add(new UserDisabled(id));
    }

    /** DISABLED → NORMAL */
    public void reEnable() {
        if (status != AccountStatus.DISABLED) {
            throw new InvalidAccountStateTransitionException(status, "re-enable");
        }
        this.status = AccountStatus.NORMAL;
        domainEvents.add(new UserActivated(id));
    }

    // ── School memberships ──

    /** Add a school membership. Rejects duplicate schoolId. */
    public void addMembership(SchoolMembership membership) {
        if (memberships.stream().anyMatch(m -> m.schoolId().equals(membership.schoolId())
                && m.status() == MembershipStatus.ACTIVE)) {
            throw new IllegalStateException("active membership already exists for school " + membership.schoolId());
        }
        this.memberships.add(membership);
    }

    /** End a school membership by schoolId. */
    public void endMembership(UUID schoolId) {
        for (int i = 0; i < memberships.size(); i++) {
            var m = memberships.get(i);
            if (m.schoolId().equals(schoolId) && m.status() == MembershipStatus.ACTIVE) {
                memberships.set(i, m.end());
                return;
            }
        }
        throw new IllegalStateException("no active membership for school " + schoolId);
    }

    /** Find primary/active memberships. */
    public List<SchoolMembership> activeMemberships() {
        return memberships.stream()
                .filter(m -> m.status() == MembershipStatus.ACTIVE)
                .toList();
    }

    public Optional<SchoolMembership> membershipFor(UUID schoolId) {
        return memberships.stream().filter(m -> m.schoolId().equals(schoolId)).findFirst();
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public UserId id() { return id; }
    public String username() { return username; }
    public AccountStatus status() { return status; }
    public String platformRole() { return platformRole; }

    public List<SchoolMembership> memberships() { return Collections.unmodifiableList(memberships); }
    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private UserId id;
        private String username;
        private String platformRole;

        public Builder id(UserId v) { this.id = v; return this; }
        public Builder username(String v) { this.username = v; return this; }
        public Builder platformRole(String v) { this.platformRole = v; return this; }
    }
}
