package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
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

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String loginName;
    private final String passwordHash;
    private final String accountStatus;
    private final Set<GrantedAuthority> authorities;
    private final List<SchoolMembershipRecord> schoolMemberships;
    private final ResolvedIdentity resolvedIdentity;

    public CampusGuinnessUserDetails(
            UUID userId,
            String loginName,
            String passwordHash,
            String accountStatus,
            Set<GrantedAuthority> authorities,
            List<SchoolMembershipRecord> schoolMemberships,
            ResolvedIdentity resolvedIdentity) {
        this.userId = userId;
        this.loginName = loginName;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus;
        this.authorities = Collections.unmodifiableSet(authorities);
        this.schoolMemberships = List.copyOf(schoolMemberships);
        this.resolvedIdentity = resolvedIdentity;
    }

    /** The resolved primary identity (role + school) for this user. */
    public ResolvedIdentity getResolvedIdentity() { return resolvedIdentity; }

    /** The domain User UUID — the single source of truth for actorId. */
    public UUID getUserId() {
        return userId;
    }

    /** ACTIVE school memberships with schoolId and roleInSchool. */
    public List<SchoolMembershipRecord> getSchoolMemberships() {
        return schoolMemberships;
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
        return "NORMAL".equals(accountStatus)
                || "LOCKED".equals(accountStatus); // LOCKED is still enabled but not non-locked
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(accountStatus);
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
