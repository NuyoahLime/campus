package com.campusguinness.identity.application.port;

public interface SecureTokenHasher {
    String hash(String rawToken);
}
