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

        if ("SUPER_ADMIN".equals(account.platformRole())) {
            if (!memberships.isEmpty()) {
                log.warn("SUPER_ADMIN user {} has {} ACTIVE school memberships",
                        account.userId(), memberships.size());
                throw denied("IDENTITY_AMBIGUOUS", "The login identity is ambiguous.");
            }
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            return new CampusGuinnessUserDetails(
                    account.userId(),
                    account.loginName(),
                    account.passwordHash(),
                    account.accountStatus(),
                    authorities,
                    schoolMemberships
            );
        }

        if (account.platformRole() != null) {
            log.warn("Unknown platform_role '{}' for user {}", account.platformRole(), account.userId());
        }

        if (memberships.isEmpty()) {
            if (account.platformRole() == null) {
                throw denied("IDENTITY_NOT_ASSIGNED", "The login identity is not assigned.");
            }
            throw denied("ACCOUNT_ROLE_NOT_READY", "The account role is not ready.");
        }

        if (memberships.size() > 1) {
            log.warn("User {} has {} ACTIVE school memberships", account.userId(), memberships.size());
            throw denied("IDENTITY_AMBIGUOUS", "The login identity is ambiguous.");
        }

        var membership = memberships.getFirst();
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

        if (authorities.isEmpty()) {
            log.warn("Normal user {} has no login authority from platform role or ACTIVE memberships",
                    account.userId());
            throw denied("ACCOUNT_ROLE_NOT_READY", "The account role is not ready.");
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

    private LoginDeniedAuthenticationException denied(String code, String message) {
        return new LoginDeniedAuthenticationException(code, message, HttpStatus.FORBIDDEN);
    }

    private AuthenticatedSchoolMembership toAuthenticatedMembership(AuthenticationMembership membership) {
        return new AuthenticatedSchoolMembership(
                membership.membershipId(),
                membership.schoolId(),
                membership.roleInSchool()
        );
    }
}
