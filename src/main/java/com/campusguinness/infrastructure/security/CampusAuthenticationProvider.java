package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.LoginCredentialCommandPort;
import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CampusAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationAccountQuery accounts;
    private final AuthenticationMembershipQuery memberships;
    private final LoginCredentialCommandPort credentials;
    private final LoginBusinessStateResolver businessStates;
    private final CampusPrincipalFactory principals;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    CampusAuthenticationProvider(
            AuthenticationAccountQuery accounts,
            AuthenticationMembershipQuery memberships,
            LoginCredentialCommandPort credentials,
            LoginBusinessStateResolver businessStates,
            CampusPrincipalFactory principals,
            PasswordEncoder passwordEncoder
    ) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.credentials = credentials;
        this.businessStates = businessStates;
        this.principals = principals;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = normalize(authentication.getName());
        String rawPassword = authentication.getCredentials() != null
                ? authentication.getCredentials().toString()
                : "";

        AuthenticationAccount account = accounts.findByLoginName(username).orElse(null);
        if (account == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw authenticationFailed();
        }

        if (!passwordEncoder.matches(rawPassword, account.passwordHash())) {
            credentials.recordPasswordFailure(account.userId());
            throw authenticationFailed();
        }

        credentials.resetPasswordFailures(account.userId());
        businessStates.requireLoginAllowed(account);

        var activeMemberships = memberships.findActiveByUserId(account.userId());
        var principal = principals.create(account, activeMemberships);
        var authenticated = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authenticated.setDetails(authentication.getDetails());
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private String normalize(String raw) {
        return raw != null ? raw.trim() : "";
    }

    private BadCredentialsException authenticationFailed() {
        return new BadCredentialsException("The username or password is invalid.");
    }
}
