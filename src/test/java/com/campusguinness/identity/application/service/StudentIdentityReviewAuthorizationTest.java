package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.SchoolAdministrationAccessQuery;
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
class StudentIdentityReviewAuthorizationTest {

    @Mock CurrentActor currentActor;
    @Mock SchoolAdministrationAccessQuery accessQuery;

    private StudentIdentityReviewAuthorization authorization;

    @BeforeEach
    void setUp() {
        authorization = new StudentIdentityReviewAuthorization(currentActor, accessQuery);
    }

    @Test
    void requireSchoolAdminReturnsCurrentActorWhenMembershipExists() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(accessQuery.hasActiveSchoolAdminMembership(actorId, schoolId)).thenReturn(true);

        UUID result = authorization.requireSchoolAdmin(schoolId);

        assertThat(result).isEqualTo(actorId);
        verify(currentActor).requireUserId();
        verify(accessQuery).hasActiveSchoolAdminMembership(actorId, schoolId);
    }

    @Test
    void requireSchoolAdminRejectsWhenTargetSchoolIsOutOfScope() {
        UUID actorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(accessQuery.hasActiveSchoolAdminMembership(actorId, schoolId)).thenReturn(false);

        assertThatThrownBy(() -> authorization.requireSchoolAdmin(schoolId))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED");
    }
}
