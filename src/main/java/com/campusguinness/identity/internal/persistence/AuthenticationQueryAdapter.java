package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter that implements {@link AuthenticationAccountQuery} using the existing
 * {@link UserJpaRepository} and {@link SchoolMembershipJpaRepository}.
 * <p>
 * Loads authentication-relevant columns and ACTIVE school memberships for role mapping.
 */
@Component
class AuthenticationQueryAdapter implements AuthenticationAccountQuery {

    private final UserJpaRepository userJpa;
    private final SchoolMembershipJpaRepository membershipJpa;

    AuthenticationQueryAdapter(UserJpaRepository userJpa, SchoolMembershipJpaRepository membershipJpa) {
        this.userJpa = userJpa;
        this.membershipJpa = membershipJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationAccount> findByLoginName(String loginName) {
        return userJpa.findByUsername(loginName)
                .map(e -> {
                    var memberships = membershipJpa
                            .findByUserIdAndStatus(e.getId(), "ACTIVE").stream()
                            .map(m -> new SchoolMembershipRecord(m.getSchoolId(), m.getRoleInSchool()))
                            .toList();
                    return new AuthenticationAccount(
                            e.getId(),
                            e.getUsername(),
                            e.getPasswordHash(),
                            e.getAccountStatus(),
                            e.getPlatformRole(),
                            e.getEmail(),
                            e.getEmailVerifiedAt(),
                            e.getRegistrationSource(),
                            memberships,
                            e.getLoginFailures(),
                            e.getLockedUntil()
                    );
                });
    }
}
