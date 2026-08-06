package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
class CampusPrincipalFactory {

    private static final Logger log = LoggerFactory.getLogger(CampusPrincipalFactory.class);

    CampusGuinnessUserDetails create(AuthenticationAccount account, List<AuthenticationMembership> memberships) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        var schoolMemberships = new java.util.ArrayList<AuthenticatedSchoolMembership>();

        if (account.platformRole() != null) {
            if ("SUPER_ADMIN".equals(account.platformRole())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            } else {
                log.warn("Unknown platform_role '{}' for user {}", account.platformRole(), account.userId());
            }
        }

        for (var membership : memberships) {
            if ("STUDENT".equals(membership.roleInSchool())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
                schoolMemberships.add(toAuthenticatedMembership(membership));
            } else if ("SCHOOL_ADMIN".equals(membership.roleInSchool())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"));
                schoolMemberships.add(toAuthenticatedMembership(membership));
            } else if ("TEACHER".equals(membership.roleInSchool())) {
                log.warn("Ignoring legacy TEACHER membership {} for user {}",
                        membership.membershipId(), account.userId());
            } else {
                log.warn("Ignoring unknown school role '{}' on membership {} for user {}",
                        membership.roleInSchool(), membership.membershipId(), account.userId());
            }
        }

        if (authorities.isEmpty()) {
            log.warn("Normal user {} has no login authority from platform role or ACTIVE memberships",
                    account.userId());
            throw new LoginDeniedAuthenticationException(
                    "ACCOUNT_ROLE_NOT_READY",
                    "The account role is not ready.",
                    HttpStatus.FORBIDDEN);
        }

        return new CampusGuinnessUserDetails(
                account.userId(),
                account.loginName(),
                account.passwordHash(),
                account.accountStatus(),
                authorities,
                schoolMemberships
        );
    }

    private AuthenticatedSchoolMembership toAuthenticatedMembership(AuthenticationMembership membership) {
        return new AuthenticatedSchoolMembership(
                membership.membershipId(),
                membership.schoolId(),
                membership.roleInSchool()
        );
    }
}
