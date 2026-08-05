package com.campusguinness.identity.application.port;

import java.util.UUID;

public interface UserCredentialCommandPort {
    void replacePasswordHash(UUID userId, String newPasswordHash);
}
