package com.campusguinness.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Security {@link UserDetails} implementation backed by
 * {@link com.campusguinness.identity.application.query.AuthenticationAccount}.
 * <p>
 * actorId is always {@link #getUserId()}, never parsed from username.
 * Password comparison is delegated to {@link org.springframework.security.crypto.password.PasswordEncoder}.
 */
public final class CampusGuinnessUserDetails implements UserDetails {

    private final UUID userId;
    private final String loginName;
    private final String passwordHash;
    private final String accountStatus;
    private final Set<GrantedAuthority> authorities;
    private final List<AuthenticatedSchoolMembership> activeSchoolMemberships;

    public CampusGuinnessUserDetails(
            UUID userId,
            String loginName,
            String passwordHash,
            String accountStatus,
            Set<GrantedAuthority> authorities,
            List<AuthenticatedSchoolMembership> activeSchoolMemberships) {
        this.userId = userId;
        this.loginName = loginName;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus;
        this.authorities = Collections.unmodifiableSet(authorities);
        this.activeSchoolMemberships = activeSchoolMemberships.stream()
                .sorted(Comparator
                        .comparing(AuthenticatedSchoolMembership::schoolId)
                        .thenComparing(AuthenticatedSchoolMembership::roleInSchool)
                        .thenComparing(AuthenticatedSchoolMembership::membershipId))
                .toList();
    }

    /** The domain User UUID — the single source of truth for actorId. */
    public UUID getUserId() {
        return userId;
    }

    public String accountStatus() {
        return accountStatus;
    }

    public List<AuthenticatedSchoolMembership> activeSchoolMemberships() {
        return activeSchoolMemberships;
    }

    public boolean hasActiveSchoolRole(UUID schoolId, String role) {
        return activeSchoolMemberships.stream()
                .anyMatch(m -> m.schoolId().equals(schoolId) && m.roleInSchool().equals(role));
    }

    // ── UserDetails contract ──

    @Override
    public String getUsername() {
        return loginName;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // credential expiry not yet implemented
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // credential expiry not yet implemented
    }

    // ── Object ──

    @Override
    public String toString() {
        return "CampusGuinnessUserDetails{userId=" + userId + ", loginName='" + loginName + "'}";
    }
}
