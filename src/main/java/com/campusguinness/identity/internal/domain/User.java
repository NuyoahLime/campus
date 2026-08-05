package com.campusguinness.identity.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User aggregate root.
 *
 * <p>SchoolMembership is a child entity inside this aggregate. It references
 * School by ID only and is written through UserRepository.
 */
public final class User {

    private final UserId id;
    private final String username;
    private AccountStatus status;
    private final String platformRole;
    private final List<SchoolMembership> memberships;
    private final List<Object> domainEvents;

    private User(Builder b, AccountStatus status) {
        this.id = b.id;
        this.username = b.username;
        this.status = status;
        this.platformRole = b.platformRole;
        this.memberships = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
    }

    public static User create(Builder builder) {
        validate(builder);
        return new User(builder, AccountStatus.PENDING_ACTIVATION);
    }

    public static User reconstitute(
            Builder builder,
            AccountStatus status,
            List<SchoolMembership> memberships
    ) {
        validate(builder);
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        var u = new User(builder, status);
        u.restoreMemberships(memberships);
        return u;
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.username == null || b.username.isBlank()) throw new IllegalArgumentException("username required");
        if (b.username.length() > 100) throw new IllegalArgumentException("username max 100 chars");
    }

    /** PENDING_ACTIVATION -> NORMAL */
    public void activate() {
        if (status != AccountStatus.PENDING_ACTIVATION) {
            throw new InvalidAccountStateTransitionException(status, "activate");
        }
        this.status = AccountStatus.NORMAL;
        domainEvents.add(new UserActivated(id));
    }

    /** NORMAL -> LOCKED */
    public void lock() {
        if (status != AccountStatus.NORMAL) {
            throw new InvalidAccountStateTransitionException(status, "lock");
        }
        this.status = AccountStatus.LOCKED;
    }

    /** LOCKED -> NORMAL */
    public void unlock() {
        if (status != AccountStatus.LOCKED) {
            throw new InvalidAccountStateTransitionException(status, "unlock");
        }
        this.status = AccountStatus.NORMAL;
    }

    /** PENDING_ACTIVATION / NORMAL / LOCKED -> DISABLED */
    public void disable() {
        if (status != AccountStatus.PENDING_ACTIVATION
                && status != AccountStatus.NORMAL
                && status != AccountStatus.LOCKED) {
            throw new InvalidAccountStateTransitionException(status, "disable");
        }
        this.status = AccountStatus.DISABLED;
        domainEvents.add(new UserDisabled(id));
    }

    /** DISABLED -> NORMAL */
    public void reEnable() {
        if (status != AccountStatus.DISABLED) {
            throw new InvalidAccountStateTransitionException(status, "re-enable");
        }
        this.status = AccountStatus.NORMAL;
        domainEvents.add(new UserActivated(id));
    }

    public SchoolMembership grantStudentMembership(
            SchoolMembershipId membershipId,
            UUID schoolId,
            Instant startedAt
    ) {
        return grantMembership(membershipId, schoolId, SchoolRole.STUDENT, startedAt);
    }

    public SchoolMembership grantSchoolAdminMembership(
            SchoolMembershipId membershipId,
            UUID schoolId,
            Instant startedAt
    ) {
        return grantMembership(membershipId, schoolId, SchoolRole.SCHOOL_ADMIN, startedAt);
    }

    public SchoolMembership endMembership(UUID schoolId, Instant endedAt) {
        var membership = activeMembershipFor(schoolId)
                .orElseThrow(() -> new IllegalStateException("no active membership for school " + schoolId));
        membership.end(endedAt);
        return membership;
    }

    public List<SchoolMembership> activeMemberships() {
        return memberships.stream()
                .filter(m -> m.status() == MembershipStatus.ACTIVE)
                .toList();
    }

    public Optional<SchoolMembership> activeMembershipFor(UUID schoolId) {
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        return memberships.stream()
                .filter(m -> m.schoolId().equals(schoolId))
                .filter(m -> m.status() == MembershipStatus.ACTIVE)
                .findFirst();
    }

    public List<SchoolMembership> membershipHistoryFor(UUID schoolId) {
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        return memberships.stream()
                .filter(m -> m.schoolId().equals(schoolId))
                .toList();
    }

    private SchoolMembership grantMembership(
            SchoolMembershipId membershipId,
            UUID schoolId,
            SchoolRole role,
            Instant startedAt
    ) {
        if (status != AccountStatus.NORMAL) {
            throw new IllegalStateException("only NORMAL users can receive school memberships");
        }
        if (membershipId == null) throw new IllegalArgumentException("schoolMembershipId required");
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (startedAt == null) throw new IllegalArgumentException("startedAt required");
        rejectDuplicateMembershipId(membershipId);
        rejectDuplicateActiveSchool(schoolId);

        var membership = SchoolMembership.start(membershipId, schoolId, role, startedAt);
        this.memberships.add(membership);
        return membership;
    }

    private void rejectDuplicateActiveSchool(UUID schoolId) {
        if (activeMembershipFor(schoolId).isPresent()) {
            throw new IllegalStateException("active membership already exists for school " + schoolId);
        }
    }

    private void rejectDuplicateMembershipId(SchoolMembershipId membershipId) {
        if (memberships.stream().anyMatch(m -> m.id().equals(membershipId))) {
            throw new IllegalStateException("membership id already exists: " + membershipId.value());
        }
    }

    private void restoreMemberships(List<SchoolMembership> restored) {
        if (restored == null) {
            throw new IllegalArgumentException("memberships required");
        }
        var ids = new HashSet<SchoolMembershipId>();
        var activeSchools = new HashSet<UUID>();
        for (var membership : restored) {
            if (membership == null) {
                throw new IllegalArgumentException("membership required");
            }
            if (!ids.add(membership.id())) {
                throw new IllegalStateException("duplicate membership id: " + membership.id().value());
            }
            if (membership.status() == MembershipStatus.ACTIVE && !activeSchools.add(membership.schoolId())) {
                throw new IllegalStateException("duplicate active membership for school " + membership.schoolId());
            }
        }
        this.memberships.addAll(restored);
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    public UserId id() { return id; }
    public String username() { return username; }
    public AccountStatus status() { return status; }
    public String platformRole() { return platformRole; }

    public List<SchoolMembership> memberships() { return List.copyOf(memberships); }
    public List<Object> domainEvents() { return List.copyOf(domainEvents); }

    public static class Builder {
        private UserId id;
        private String username;
        private String platformRole;

        public Builder id(UserId v) { this.id = v; return this; }
        public Builder username(String v) { this.username = v; return this; }
        public Builder platformRole(String v) { this.platformRole = v; return this; }
    }
}
