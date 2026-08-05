package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.InvitationCodeHasher;
import com.campusguinness.identity.application.port.PasswordHasher;
import org.springframework.stereotype.Component;

@Component
class PasswordHasherInvitationCodeHasher implements InvitationCodeHasher {

    private final PasswordHasher passwordHasher;

    PasswordHasherInvitationCodeHasher(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    @Override
    public String hash(String rawCode) {
        return passwordHasher.hash(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String storedHash) {
        return passwordHasher.matches(rawCode, storedHash);
    }
}
