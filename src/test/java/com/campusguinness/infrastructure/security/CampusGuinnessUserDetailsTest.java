package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CampusGuinnessUserDetailsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test void exposesStablePrincipalFields() {
        var ud = principal("NORMAL", Set.of(), List.of());
        assertThat(ud.getUserId()).isEqualTo(USER_ID);
        assertThat(ud.getUsername()).isEqualTo("u");
        assertThat(ud.getPassword()).isEqualTo("hash");
        assertThat(ud.accountStatus()).isEqualTo("NORMAL");
    }

    @Test void userDetailsStatusMethodsDoNotPerformBusinessStateChecks() {
        for (String status : List.of("NORMAL", "LOCKED", "DISABLED", "PENDING_ACTIVATION")) {
            var ud = principal(status, Set.of(), List.of());
            assertThat(ud.isEnabled()).isTrue();
            assertThat(ud.isAccountNonLocked()).isTrue();
            assertThat(ud.isAccountNonExpired()).isTrue();
            assertThat(ud.isCredentialsNonExpired()).isTrue();
        }
    }

    @Test void authoritiesPreservedAndUnmodifiable() {
        Set<GrantedAuthority> auths = Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        var ud = principal("NORMAL", auths, List.of());
        assertThat(ud.getAuthorities()).extracting("authority").containsExactly("ROLE_SUPER_ADMIN");
        assertThatThrownBy(() -> ((java.util.Collection<?>) ud.getAuthorities()).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void schoolMembershipsAreSortedAndUnmodifiable() {
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        var first = new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolB, "SCHOOL_ADMIN");
        var second = new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolA, "STUDENT");
        var ud = principal("NORMAL", Set.of(), List.of(first, second));

        assertThat(ud.activeSchoolMemberships()).hasSize(2);
        assertThat(ud.hasActiveSchoolRole(schoolA, "STUDENT")).isTrue();
        assertThat(ud.hasActiveSchoolRole(schoolA, "SCHOOL_ADMIN")).isFalse();
        assertThatThrownBy(() -> ud.activeSchoolMemberships().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void toStringExcludesPasswordHash() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "secret", "NORMAL", Set.of(), List.of());
        assertThat(ud.toString()).doesNotContain("secret");
    }

    private CampusGuinnessUserDetails principal(
            String status,
            Set<GrantedAuthority> authorities,
            List<AuthenticatedSchoolMembership> memberships
    ) {
        return new CampusGuinnessUserDetails(USER_ID, "u", "hash", status, authorities, memberships);
    }
}
