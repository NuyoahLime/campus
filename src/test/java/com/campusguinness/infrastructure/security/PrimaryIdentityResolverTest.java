package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PrimaryIdentityResolverTest {
    private final PrimaryIdentityResolver resolver = new PrimaryIdentityResolver();
    private final UUID userId = UUID.randomUUID();
    private final UUID schoolId = UUID.randomUUID();

    @Test void superAdminWithoutMembership() {
        var r = resolver.resolve(account("SUPER_ADMIN", List.of()));
        assertThat(r.primaryRole()).isEqualTo("SUPER_ADMIN");
        assertThat(r.primarySchoolId()).isNull();
        assertThat(r.isError()).isFalse();
    }

    @Test void superAdminWithMembershipIsAmbiguous() {
        var r = resolver.resolve(account("SUPER_ADMIN", List.of(new SchoolMembershipRecord(schoolId, "TEACHER"))));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_AMBIGUOUS");
    }

    @Test void singleSchoolMembershipResolves() {
        var r = resolver.resolve(account(null, List.of(new SchoolMembershipRecord(schoolId, "TEACHER"))));
        assertThat(r.primaryRole()).isEqualTo("TEACHER");
        assertThat(r.primarySchoolId()).isEqualTo(schoolId);
    }

    @Test void multipleMembershipsIsAmbiguous() {
        var r = resolver.resolve(account(null, List.of(new SchoolMembershipRecord(schoolId, "TEACHER"), new SchoolMembershipRecord(UUID.randomUUID(), "STUDENT"))));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_AMBIGUOUS");
    }

    @Test void noMembershipAndNoPlatformRole() {
        var r = resolver.resolve(account(null, List.of()));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_NOT_ASSIGNED");
    }

    @Test void unknownSchoolRoleIsInvalid() {
        var r = resolver.resolve(account(null, List.of(new SchoolMembershipRecord(schoolId, "INVALID_ROLE"))));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_INVALID");
    }

    @Test void studentResolvesCorrectly() {
        var r = resolver.resolve(account(null, List.of(new SchoolMembershipRecord(schoolId, "STUDENT"))));
        assertThat(r.primaryRole()).isEqualTo("STUDENT");
    }

    @Test void schoolAdminResolvesCorrectly() {
        var r = resolver.resolve(account(null, List.of(new SchoolMembershipRecord(schoolId, "SCHOOL_ADMIN"))));
        assertThat(r.primaryRole()).isEqualTo("SCHOOL_ADMIN");
    }

    @Test void unknownPlatformRoleIsInvalid() {
        var r = resolver.resolve(account("UNKNOWN", List.of()));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_INVALID");
    }

    @Test void registeredUserWithoutMembershipResolves() {
        var r = resolver.resolve(account("REGISTERED_USER", List.of()));
        assertThat(r.primaryRole()).isEqualTo("REGISTERED_USER");
        assertThat(r.primarySchoolId()).isNull();
        assertThat(r.isError()).isFalse();
    }

    @Test void registeredUserWithMembershipIsAmbiguous() {
        var r = resolver.resolve(account("REGISTERED_USER",
                List.of(new SchoolMembershipRecord(schoolId, "STUDENT"))));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_AMBIGUOUS");
    }

    private AuthenticationAccount account(String platformRole, List<SchoolMembershipRecord> memberships) {
        return new AuthenticationAccount(userId, "test", "hash", "NORMAL", platformRole, memberships, 0, null);
    }
}
