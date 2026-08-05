package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminActivationServiceTest {

    @Mock SchoolAdminActivationTransaction transaction;

    @Test
    void passwordConfirmationMismatchFailsBeforeTransaction() {
        var service = new SchoolAdminActivationService(transaction);

        assertThatThrownBy(() -> service.activate(new ActivateSchoolAdminCommand(
                "admin", "code", "Password123!", "Different123!")))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("PASSWORD_CONFIRMATION_MISMATCH");
        verifyNoInteractions(transaction);
    }

    @Test
    void invalidCredentialOutcomeMapsToStableFailureCode() {
        when(transaction.tryActivate(any())).thenReturn(ActivationOutcome.INVALID_CREDENTIAL);
        var service = new SchoolAdminActivationService(transaction);

        assertThatThrownBy(() -> service.activate(new ActivateSchoolAdminCommand(
                "admin", "code", "Password123!", "Password123!")))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("INVITATION_ACTIVATION_FAILED");
    }
}
