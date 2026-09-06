package com.campusguinness.ranking.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class L3PublicIdentityMasker {
    private L3PublicIdentityMasker() {}

    static String anonymousStudentName() {
        return "\u533f\u540d\u9009\u624b";
    }

    static String maskedStudentName(UUID studentId) {
        return "\u9009\u624b-" + hashToken(studentId);
    }

    private static String hashToken(UUID studentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(studentId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 5).toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "Cannot generate ranking: public student identity token is unavailable.", ex);
        }
    }
}
