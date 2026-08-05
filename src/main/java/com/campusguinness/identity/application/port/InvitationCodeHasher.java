package com.campusguinness.identity.application.port;

public interface InvitationCodeHasher {
    String hash(String rawCode);
    boolean matches(String rawCode, String storedHash);
}
