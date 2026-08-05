package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.InvitationCodeGenerator;
import com.campusguinness.identity.application.port.PlaceholderCredentialGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
class SecureRandomInvitationCodeGenerator implements InvitationCodeGenerator, PlaceholderCredentialGenerator {

    private static final int RANDOM_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
