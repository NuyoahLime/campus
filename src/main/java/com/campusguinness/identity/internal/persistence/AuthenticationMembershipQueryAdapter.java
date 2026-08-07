package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
class AuthenticationMembershipQueryAdapter implements AuthenticationMembershipQuery {

    private final SchoolMembershipJpaRepository memberships;

    AuthenticationMembershipQueryAdapter(SchoolMembershipJpaRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthenticationMembership> findActiveByUserId(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        return memberships.findAllByUserIdAndStatusOrderByStartedAtAscIdAsc(userId, "ACTIVE").stream()
                .map(e -> new AuthenticationMembership(
                        e.getId(),
                        e.getSchoolId(),
                        e.getRoleInSchool()
                ))
                .toList();
    }
}
