package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CampusGuinnessUserDetailsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test void getUserIdReturnsUuid() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "hash", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.getUserId()).isEqualTo(USER_ID);
    }

    @Test void getUsernameReturnsLoginName() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "testuser", "hash", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.getUsername()).isEqualTo("testuser");
    }

    @Test void getPasswordReturnsHash() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "$2a$12$abcdef", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.getPassword()).isEqualTo("$2a$12$abcdef");
    }

    @Test void normalIsEnabledAndNonLocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.isEnabled()).isTrue();
        assertThat(ud.isAccountNonLocked()).isTrue();
        assertThat(ud.isAccountNonExpired()).isTrue();
        assertThat(ud.isCredentialsNonExpired()).isTrue();
    }

    @Test void lockedIsEnabledButNotNonLocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "LOCKED", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.isEnabled()).isTrue();
        assertThat(ud.isAccountNonLocked()).isFalse();
    }

    @Test void disabledNotEnabled() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "DISABLED", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.isEnabled()).isFalse();
    }

    @Test void pendingActivationNotEnabled() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "PENDING_ACTIVATION", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.isEnabled()).isFalse();
    }

    @Test void authoritiesPreserved() {
        Set<GrantedAuthority> auths = Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", null, auths, java.util.List.of(), null);
        assertThat(ud.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
        assertThat(ud.getAuthorities()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test void authoritiesIsUnmodifiable() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThatThrownBy(() -> ((java.util.Collection<GrantedAuthority>) ud.getAuthorities()).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void toStringExcludesPasswordHash() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "secret", "NORMAL", null, Set.of(), java.util.List.of(), null);
        assertThat(ud.toString()).doesNotContain("secret");
    }

    @Test void futureTemporaryLockIsLocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL",
                Instant.now().plusSeconds(60), Set.of(), java.util.List.of(), null);
        assertThat(ud.isAccountNonLocked()).isFalse();
    }

    @Test void expiredTemporaryLockIsUnlocked() {
        var ud = new CampusGuinnessUserDetails(USER_ID, "u", "h", "NORMAL",
                Instant.now().minusSeconds(60), Set.of(), java.util.List.of(), null);
        assertThat(ud.isAccountNonLocked()).isTrue();
    }
}
