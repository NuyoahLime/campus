package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserCredentialCommandPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
class UserCredentialCommandAdapter implements UserCredentialCommandPort {

    private final UserJpaRepository jpa;

    UserCredentialCommandAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void replacePasswordHash(UUID userId, String newPasswordHash) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash required");
        }
        var entity = jpa.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        entity.setPasswordHash(newPasswordHash);
        entity.setUpdatedAt(Instant.now());
        jpa.save(entity);
    }
}
