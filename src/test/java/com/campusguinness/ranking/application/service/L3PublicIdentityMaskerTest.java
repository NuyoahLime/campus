package com.campusguinness.ranking.application.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class L3PublicIdentityMaskerTest {

    @Test
    void maskedStudentNameIsDeterministic() {
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        String first = L3PublicIdentityMasker.maskedStudentName(studentId);
        String second = L3PublicIdentityMasker.maskedStudentName(studentId);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("选手-");
    }

    @Test
    void maskedStudentNameDoesNotExposeRawUuidSubstring() {
        UUID studentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        String masked = L3PublicIdentityMasker.maskedStudentName(studentId);

        assertThat(masked).doesNotContain(studentId.toString().replace("-", ""));
    }

    @Test
    void anonymousStudentNameRemainsAnonymous() {
        assertThat(L3PublicIdentityMasker.anonymousStudentName()).isEqualTo("匿名选手");
    }
}
