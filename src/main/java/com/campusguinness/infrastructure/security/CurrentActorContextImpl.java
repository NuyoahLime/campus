package com.campusguinness.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorContextImpl implements CurrentActorContext {

    @Override
    public ActorContext require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CampusGuinnessUserDetails user)) {
            throw new AccessDeniedException("Not authenticated");
        }
        var identity = user.getResolvedIdentity();
        if (identity == null || identity.isError()) {
            throw new AccessDeniedException("Invalid actor identity");
        }
        if (!"SUPER_ADMIN".equals(identity.primaryRole()) && identity.primarySchoolId() == null) {
            throw new AccessDeniedException("School identity required");
        }
        return new ActorContext(user.getUserId(), identity.primaryRole(), identity.primarySchoolId());
    }
}
