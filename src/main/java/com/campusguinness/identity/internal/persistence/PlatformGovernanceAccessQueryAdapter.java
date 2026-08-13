package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.PlatformGovernanceAccessQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class PlatformGovernanceAccessQueryAdapter implements PlatformGovernanceAccessQuery {

    private final UserJpaRepository users;
    private final SchoolMembershipJpaRepository memberships;

    PlatformGovernanceAccessQueryAdapter(
            UserJpaRepository users,
            SchoolMembershipJpaRepository memberships
    ) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAuthoritativeSuperAdminIdentity(UUID userId) {
        return userId != null
                && users.existsByIdAndAccountStatusAndPlatformRole(userId, "NORMAL", "SUPER_ADMIN")
                && !memberships.existsByUserIdAndStatus(userId, "ACTIVE");
    }
}
