package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
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
    private final String platformRole;
    private final String email;
    private final Instant emailVerifiedAt;
    private final String registrationSource;
    private final Instant lockedUntil;
    private final Set<GrantedAuthority> authorities;
    private final List<SchoolMembershipRecord> schoolMemberships;
    private final ResolvedIdentity resolvedIdentity;

    public CampusGuinnessUserDetails(
            UUID userId,
            String loginName,
            String passwordHash,
            String accountStatus,
            String platformRole,
            String email,
            Instant emailVerifiedAt,
            String registrationSource,
            Instant lockedUntil,
            Set<GrantedAuthority> authorities,
            List<SchoolMembershipRecord> schoolMemberships,
            ResolvedIdentity resolvedIdentity) {
        this.userId = userId;
        this.loginName = loginName;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus;
        this.platformRole = platformRole;
        this.email = email;
        this.emailVerifiedAt = emailVerifiedAt;
        this.registrationSource = registrationSource;
        this.lockedUntil = lockedUntil;
        this.authorities = Collections.unmodifiableSet(authorities);
        this.schoolMemberships = List.copyOf(schoolMemberships);
        this.resolvedIdentity = resolvedIdentity;
    }

    public CampusGuinnessUserDetails(
            UUID userId,
            String loginName,
            String passwordHash,
            String accountStatus,
            Instant lockedUntil,
            Set<GrantedAuthority> authorities,
            List<SchoolMembershipRecord> schoolMemberships,
            ResolvedIdentity resolvedIdentity) {
        this(userId, loginName, passwordHash, accountStatus, null, null, null,
                "ADMIN_PROVISIONED", lockedUntil, authorities, schoolMemberships, resolvedIdentity);
    }

    /** The resolved primary identity (role + school) for this user. */
    public ResolvedIdentity getResolvedIdentity() { return resolvedIdentity; }
    public String getAccountStatusValue() { return accountStatus; }
    public String getPlatformRole() { return platformRole; }
    public String getEmail() { return email; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public String getRegistrationSource() { return registrationSource; }

    public boolean requiresEmailVerification() {
        return "PUBLIC".equals(registrationSource)
                && "REGISTERED_USER".equals(platformRole)
                && emailVerifiedAt == null;
    }

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

    /** Returns true unless account is LOCKED or locked_until is in the future. */
    @Override
    public boolean isAccountNonLocked() {
        if ("LOCKED".equals(accountStatus)) return false;
        return lockedUntil == null || !lockedUntil.isAfter(Instant.now());
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
