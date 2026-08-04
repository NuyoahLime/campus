package com.campusguinness.infrastructure.security;

import java.util.List;
import java.util.UUID;

import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;

/**
 * Unified authentication context response returned by /auth/login and /auth/me.
 * Contains user identity, platform role, all granted authorities, and ACTIVE school memberships.
 * Never exposes password hash, membership IDs, or internal entity details.
 */
public record AuthContextResponse(
        UUID userId,
        String username,
        String accountStatus,
        String platformRole,
        List<String> roles,
        List<SchoolMembershipItem> schoolMemberships,
        String primaryRole,
        UUID primarySchoolId,
        boolean onboardingRequired
) {
    public record SchoolMembershipItem(UUID schoolId, String roleInSchool) {}

    public static AuthContextResponse from(CampusGuinnessUserDetails user) {
        var roles = user.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .sorted()
                .toList();

        String platformRole = user.getPlatformRole();

        var memberships = user.getSchoolMemberships().stream()
                .map(m -> new SchoolMembershipItem(m.schoolId(), m.roleInSchool()))
                .toList();

        // Use PrimaryIdentityResolver result via user details
        var identity = user.getResolvedIdentity();
        String primaryRole = identity != null ? identity.primaryRole() : null;
        UUID primarySchoolId = identity != null ? identity.primarySchoolId() : null;

        return new AuthContextResponse(
                user.getUserId(), user.getUsername(),
                user.getAccountStatusValue(),
                platformRole, roles, memberships,
                primaryRole, primarySchoolId,
                "REGISTERED_USER".equals(primaryRole)
        );
    }
}
