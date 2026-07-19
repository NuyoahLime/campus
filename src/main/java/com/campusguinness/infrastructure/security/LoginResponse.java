package com.campusguinness.infrastructure.security;

import java.util.UUID;

public record LoginResponse(UUID id, String username, String status, String platformRole) {

    public static LoginResponse from(CampusGuinnessUserDetails user) {
        return new LoginResponse(user.getUserId(), user.getUsername(),
                user.isEnabled() && user.isAccountNonLocked() ? "NORMAL" : "LOCKED",
                user.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ? "SUPER_ADMIN" : null);
    }
}
