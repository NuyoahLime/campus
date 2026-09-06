package com.campusguinness.ranking.application.service;

import java.util.Locale;
import java.util.UUID;

final class L3PublicIdentityMasker {
    private L3PublicIdentityMasker() {}

    static String anonymousStudentName() {
        return "匿名选手";
    }

    static String maskedStudentName(UUID studentId) {
        String token = studentId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "选手-" + token;
    }
}
