package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.PlatformGovernanceAccessQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformGovernanceAuthorizationTest {

    @Mock CurrentActor currentActor;
    @Mock PlatformGovernanceAccessQuery accessQuery;

    private PlatformGovernanceAuthorization authorization;

    @BeforeEach
    void setUp() {
        authorization = new PlatformGovernanceAuthorization(currentActor, accessQuery);
    }

    @Test
    void returnsCurrentActorForAuthoritativeSuperAdmin() {
        UUID actorId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(accessQuery.hasAuthoritativeSuperAdminIdentity(actorId)).thenReturn(true);

        assertThat(authorization.requireSuperAdmin()).isEqualTo(actorId);

        verify(accessQuery).hasAuthoritativeSuperAdminIdentity(actorId);
    }

    @Test
    void rejectsAuthorityThatIsNotBackedByAuthoritativeIdentity() {
        UUID actorId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(accessQuery.hasAuthoritativeSuperAdminIdentity(actorId)).thenReturn(false);

        assertThatThrownBy(authorization::requireSuperAdmin)
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("PLATFORM_GOVERNANCE_DENIED");
    }
}
