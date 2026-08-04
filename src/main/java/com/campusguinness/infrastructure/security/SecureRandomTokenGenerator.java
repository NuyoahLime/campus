package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.SecureTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomTokenGenerator implements SecureTokenGenerator {

    static final int RANDOM_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureRandomTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
