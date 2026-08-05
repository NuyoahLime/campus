package com.campusguinness.identity.application.service;

import java.util.List;
import java.util.UUID;

public record RegisterStudentCommand(
        String username,
        String password,
        String confirmPassword,
        String realName,
        UUID schoolId,
        String studentNumber,
        String grade,
        String className,
        List<String> proofFileKeys
) {
    public RegisterStudentCommand {
        proofFileKeys = proofFileKeys == null ? List.of() : List.copyOf(proofFileKeys);
    }

    @Override
    public String toString() {
        return "RegisterStudentCommand{username='" + username
                + "', password=[REDACTED], confirmPassword=[REDACTED], schoolId=" + schoolId + "}";
    }
}
