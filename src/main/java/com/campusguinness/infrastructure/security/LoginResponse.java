package com.campusguinness.infrastructure.security;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String username,
        String accountStatus,
        List<String> authorities,
        List<AuthenticatedSchoolMembership> schoolMemberships
) {

    public static LoginResponse from(CampusGuinnessUserDetails user) {
        return new LoginResponse(
                user.getUserId(),
                user.getUsername(),
                user.accountStatus(),
                user.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList(),
                user.activeSchoolMemberships().stream()
                        .sorted(Comparator
                                .comparing(AuthenticatedSchoolMembership::schoolId)
                                .thenComparing(AuthenticatedSchoolMembership::roleInSchool)
                                .thenComparing(AuthenticatedSchoolMembership::membershipId))
                        .toList()
        );
    }
}
