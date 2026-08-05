package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
class AuthenticationMembershipQueryAdapter implements AuthenticationMembershipQuery {

    private static final List<String> AUTHENTICATION_ROLES = List.of("STUDENT", "SCHOOL_ADMIN");

    private final SchoolMembershipJpaRepository memberships;

    AuthenticationMembershipQueryAdapter(SchoolMembershipJpaRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthenticationMembership> findActiveByUserId(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        return memberships.findAllByUserIdAndStatusAndRoleInSchoolInOrderByStartedAtAscIdAsc(
                        userId,
                        "ACTIVE",
                        AUTHENTICATION_ROLES
                ).stream()
                .map(e -> new AuthenticationMembership(
                        e.getId(),
                        e.getSchoolId(),
                        e.getRoleInSchool()
                ))
                .toList();
    }
}
