package com.campusguinness.identity.application.port;

import java.util.UUID;

public interface LoginCredentialCommandPort {
    void recordPasswordFailure(UUID userId);
    void resetPasswordFailures(UUID userId);
}
