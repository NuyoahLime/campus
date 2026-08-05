package com.campusguinness.identity.application.query;

import java.util.List;
import java.util.UUID;

public interface AuthenticationMembershipQuery {
    List<AuthenticationMembership> findActiveByUserId(UUID userId);
}
