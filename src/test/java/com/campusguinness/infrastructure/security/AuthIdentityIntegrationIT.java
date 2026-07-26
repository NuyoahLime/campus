package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuthIdentityIntegrationIT {
    private final PrimaryIdentityResolver resolver = new PrimaryIdentityResolver();
    private final UUID userId = UUID.randomUUID();
    private final UUID schoolId = UUID.randomUUID();

    @Test void nullIdentityReturnsError() {
        var r = resolver.resolve(account(null, java.util.List.of()));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_NOT_ASSIGNED");
    }

    @Test void superAdminWithSchoolAmbiguous() {
        var r = resolver.resolve(account("SUPER_ADMIN", java.util.List.of(new com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord(schoolId, "TEACHER"))));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_AMBIGUOUS");
    }

    @Test void unknownRoleInvalid() {
        var r = resolver.resolve(account("UNKNOWN", java.util.List.of()));
        assertThat(r.errorCode()).isEqualTo("IDENTITY_INVALID");
    }

    private com.campusguinness.identity.application.query.AuthenticationAccount account(String pr, java.util.List<com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord> m) {
        return new com.campusguinness.identity.application.query.AuthenticationAccount(userId, "t", "h", "NORMAL", pr, m);
    }
}
