package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.port.PasswordPolicy;
import org.springframework.stereotype.Service;

@Service
public class SchoolAdminActivationService {

    private final SchoolAdminActivationTransaction transaction;

    public SchoolAdminActivationService(SchoolAdminActivationTransaction transaction) {
        this.transaction = transaction;
    }

    public void activate(ActivateSchoolAdminCommand command) {
        if (command == null) throw new IllegalArgumentException("command required");
        if (command.newPassword() == null || !command.newPassword().equals(command.confirmPassword())) {
            throw new IdentityApplicationException("PASSWORD_CONFIRMATION_MISMATCH", "Password confirmation does not match.");
        }
        try {
            PasswordPolicy.validate(command.newPassword());
        } catch (InvalidPasswordException ex) {
            throw new IdentityApplicationException("PASSWORD_POLICY_VIOLATION", "Password does not satisfy policy.");
        }

        ActivationOutcome outcome = transaction.tryActivate(command);
        switch (outcome) {
            case SUCCESS -> {
            }
            case EXPIRED -> throw new IdentityApplicationException("INVITATION_EXPIRED", "Invitation has expired.");
            case ACCOUNT_NOT_ACTIVATABLE -> throw new IdentityApplicationException(
                    "ACCOUNT_NOT_ACTIVATABLE", "Account cannot be activated.");
            case MEMBERSHIP_CONFLICT -> throw new IdentityApplicationException(
                    "SCHOOL_ADMIN_MEMBERSHIP_CONFLICT", "School admin membership already exists.");
            case INVALID_CREDENTIAL -> throw new IdentityApplicationException(
                    "INVITATION_ACTIVATION_FAILED", "Invitation activation failed.");
        }
    }
}
