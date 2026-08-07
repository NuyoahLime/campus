package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Compatibility UserDetailsService. The actual login flow uses
 * CampusAuthenticationProvider so password verification happens before any
 * business-state checks.
 */
@Component
public class CampusGuinnessUserDetailsService implements UserDetailsService {

    private final AuthenticationAccountQuery accountQuery;
    private final AuthenticationMembershipQuery membershipQuery;
    private final CampusPrincipalFactory principalFactory;

    public CampusGuinnessUserDetailsService(
            AuthenticationAccountQuery accountQuery,
            AuthenticationMembershipQuery membershipQuery,
            CampusPrincipalFactory principalFactory
    ) {
        this.accountQuery = accountQuery;
        this.membershipQuery = membershipQuery;
        this.principalFactory = principalFactory;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username != null ? username.trim() : "";
        AuthenticationAccount account = accountQuery.findByLoginName(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return principalFactory.create(account, membershipQuery.findActiveByUserId(account.userId()));
    }
}
