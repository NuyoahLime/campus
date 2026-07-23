package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Loads {@link CampusGuinnessUserDetails} from {@link AuthenticationAccountQuery}.
 * <p>
 * Platform role mapping uses a strict whitelist. Unknown platform_role values
 * are rejected with a WARN log and no authority is granted.
 * School-level roles are NOT loaded — deferred to TASK-AUTH-ROLE-MAPPING-001.
 */
@Component
public class CampusGuinnessUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CampusGuinnessUserDetailsService.class);

    /** Known platform roles that map to Spring Security authorities. */
    private static final Set<String> KNOWN_PLATFORM_ROLES = Set.of("SUPER_ADMIN");

    /** Known school roles that map to Spring Security authorities. */
    private static final Set<String> KNOWN_SCHOOL_ROLES = Set.of("STUDENT", "TEACHER", "SCHOOL_ADMIN");

    private final AuthenticationAccountQuery accountQuery;

    public CampusGuinnessUserDetailsService(AuthenticationAccountQuery accountQuery) {
        this.accountQuery = accountQuery;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = normalizeLoginName(username);

        AuthenticationAccount account = accountQuery.findByLoginName(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        Set<GrantedAuthority> authorities = new HashSet<>();
        mapPlatformAuthorities(account, authorities);
        mapSchoolAuthorities(account, authorities);

        return new CampusGuinnessUserDetails(
                account.userId(),
                account.loginName(),
                account.passwordHash(),
                account.accountStatus(),
                authorities,
                account.memberships()
        );
    }

    /**
     * Normalize login name: trim whitespace. Future: lowercase if case-insensitive.
     */
    private String normalizeLoginName(String raw) {
        return raw != null ? raw.trim() : "";
    }

    /**
     * Map platform_role to GrantedAuthority using strict whitelist.
     * Unknown values are rejected with a WARN log.
     */
    private void mapPlatformAuthorities(AuthenticationAccount account, Set<GrantedAuthority> authorities) {
        String role = account.platformRole();
        if (role != null) {
            if (KNOWN_PLATFORM_ROLES.contains(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            } else {
                log.warn("Unknown platform_role '{}' for user {} — denying platform authority",
                        role, account.userId());
            }
        }
    }

    /**
     * Map school membership roles to GrantedAuthority using strict whitelist.
     * Only ACTIVE memberships are loaded by the query adapter.
     * Unknown role_in_school values are logged and not granted.
     */
    private void mapSchoolAuthorities(AuthenticationAccount account, Set<GrantedAuthority> authorities) {
        for (var m : account.memberships()) {
            String role = m.roleInSchool();
            if (KNOWN_SCHOOL_ROLES.contains(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            } else {
                log.warn("Unknown role_in_school '{}' for user {} in school {} — not granting authority",
                        role, account.userId(), m.schoolId());
            }
        }
    }
}
