package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampusPrincipalFactoryTest {

    private final CampusPrincipalFactory factory = new CampusPrincipalFactory();
    private final UUID userId = UUID.randomUUID();

    @Test
    void mapsOnlyFormalAuthoritiesAndKeepsMembershipScopesWithIds() {
        UUID studentMembershipId = UUID.randomUUID();
        UUID adminMembershipId = UUID.randomUUID();
        UUID studentSchoolId = UUID.randomUUID();
        UUID adminSchoolId = UUID.randomUUID();

        var principal = factory.create(
                account("SUPER_ADMIN"),
                List.of(
                        new AuthenticationMembership(studentMembershipId, studentSchoolId, "STUDENT"),
                        new AuthenticationMembership(adminMembershipId, adminSchoolId, "SCHOOL_ADMIN")
                ));

        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_STUDENT", "ROLE_SCHOOL_ADMIN");
        assertThat(principal.activeSchoolMemberships())
                .containsExactlyInAnyOrder(
                        new AuthenticatedSchoolMembership(studentMembershipId, studentSchoolId, "STUDENT"),
                        new AuthenticatedSchoolMembership(adminMembershipId, adminSchoolId, "SCHOOL_ADMIN"));
    }

    @Test
    void ignoresLegacyTeacherMembershipAndFailsClosedWhenNoFormalRoleRemains() {
        assertThatThrownBy(() -> factory.create(
                account(null),
                List.of(new AuthenticationMembership(UUID.randomUUID(), UUID.randomUUID(), "TEACHER"))))
                .isInstanceOf(LoginDeniedAuthenticationException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_ROLE_NOT_READY");
    }

    @Test
    void unknownPlatformRoleIsNotMappedDynamically() {
        assertThatThrownBy(() -> factory.create(account("REGISTERED_USER"), List.of()))
                .isInstanceOf(LoginDeniedAuthenticationException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_ROLE_NOT_READY");
    }

    private AuthenticationAccount account(String platformRole) {
        return new AuthenticationAccount(userId, "u", "hash", "NORMAL", platformRole, null);
    }
}
