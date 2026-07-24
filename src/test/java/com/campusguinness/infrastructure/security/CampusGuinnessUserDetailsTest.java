package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CampusGuinnessUserDetailsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test void getUserIdReturnsUuid() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "hash", "NORMAL", Set.of(), java.util.List.of());
        assertThat(ud.getUserId()).isEqualTo(USER_ID);
    }

    @Test void getUsernameReturnsLoginName() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "testuser", "hash", "NORMAL", Set.of(), java.util.List.of());
        assertThat(ud.getUsername()).isEqualTo("testuser");
    }

    @Test void getPasswordReturnsHash() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "$2a$12$abcdef", "NORMAL", Set.of(), java.util.List.of());
        assertThat(ud.getPassword()).isEqualTo("$2a$12$abcdef");
    }

    @Test void normalIsEnabledAndNonLocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", Set.of(), java.util.List.of());
        assertThat(ud.isEnabled()).isTrue();
        assertThat(ud.isAccountNonLocked()).isTrue();
        assertThat(ud.isAccountNonExpired()).isTrue();
        assertThat(ud.isCredentialsNonExpired()).isTrue();
    }

    @Test void lockedIsEnabledButNotNonLocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "LOCKED", Set.of(), java.util.List.of());
        assertThat(ud.isEnabled()).isTrue();
        assertThat(ud.isAccountNonLocked()).isFalse();
    }

    @Test void disabledNotEnabled() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "DISABLED", Set.of(), java.util.List.of());
        assertThat(ud.isEnabled()).isFalse();
    }

    @Test void pendingActivationNotEnabled() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "PENDING_ACTIVATION", Set.of(), java.util.List.of());
        assertThat(ud.isEnabled()).isFalse();
    }

    @Test void authoritiesPreserved() {
        Set<GrantedAuthority> auths = Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", auths, java.util.List.of());
        assertThat(ud.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
        assertThat(ud.getAuthorities()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test void authoritiesIsUnmodifiable() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", Set.of(), java.util.List.of());
        assertThatThrownBy(() -> ((java.util.Collection<GrantedAuthority>) ud.getAuthorities()).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void toStringExcludesPasswordHash() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "secret", "NORMAL", Set.of(), java.util.List.of());
        assertThat(ud.toString()).doesNotContain("secret");
    }
}
