package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampusPrincipalFactoryTest {

    private final CampusPrincipalFactory factory = new CampusPrincipalFactory();
    private final UUID userId = UUID.randomUUID();

    @Test
    void superAdminCannotHaveSchoolMemberships() {
        UUID studentMembershipId = UUID.randomUUID();
        UUID adminMembershipId = UUID.randomUUID();
        UUID studentSchoolId = UUID.randomUUID();
        UUID adminSchoolId = UUID.randomUUID();

        assertDenied(
                account("SUPER_ADMIN"),
                List.of(
                        new AuthenticationMembership(studentMembershipId, studentSchoolId, "STUDENT"),
                        new AuthenticationMembership(adminMembershipId, adminSchoolId, "SCHOOL_ADMIN")
                ),
                "IDENTITY_AMBIGUOUS");
    }

    @Test
    void normalUserWithExactlyOneStudentMembershipReceivesOnlyStudentAuthority() {
        UUID membershipId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        var principal = factory.create(
                account(null),
                List.of(new AuthenticationMembership(membershipId, schoolId, "STUDENT")));

        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_STUDENT");
        assertThat(principal.activeSchoolMemberships())
                .containsExactly(new AuthenticatedSchoolMembership(membershipId, schoolId, "STUDENT"));
    }

    @Test
    void normalUserWithExactlyOneSchoolAdminMembershipReceivesOnlySchoolAdminAuthority() {
        UUID membershipId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        var principal = factory.create(
                account(null),
                List.of(new AuthenticationMembership(membershipId, schoolId, "SCHOOL_ADMIN")));

        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SCHOOL_ADMIN");
        assertThat(principal.activeSchoolMemberships())
                .containsExactly(new AuthenticatedSchoolMembership(membershipId, schoolId, "SCHOOL_ADMIN"));
    }

    @Test
    void normalUserWithoutMembershipIsNotAssignedAnIdentity() {
        assertDenied(account(null), List.of(), "IDENTITY_NOT_ASSIGNED");
    }

    @Test
    void normalUserWithMultipleFormalMembershipsIsAmbiguous() {
        assertDenied(
                account(null),
                List.of(
                        new AuthenticationMembership(UUID.randomUUID(), UUID.randomUUID(), "STUDENT"),
                        new AuthenticationMembership(UUID.randomUUID(), UUID.randomUUID(), "SCHOOL_ADMIN")
                ),
                "IDENTITY_AMBIGUOUS");
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

    private void assertDenied(
            AuthenticationAccount account,
            List<AuthenticationMembership> memberships,
            String code
    ) {
        assertThatThrownBy(() -> factory.create(account, memberships))
                .isInstanceOf(LoginDeniedAuthenticationException.class)
                .satisfies(error -> {
                    var denied = (LoginDeniedAuthenticationException) error;
                    assertThat(denied.code()).isEqualTo(code);
                    assertThat(denied.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    private AuthenticationAccount account(String platformRole) {
        return new AuthenticationAccount(userId, "u", "hash", "NORMAL", platformRole, null);
    }
}
