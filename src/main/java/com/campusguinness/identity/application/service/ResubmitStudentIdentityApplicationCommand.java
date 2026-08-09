package com.campusguinness.identity.application.service;

import java.util.List;

public record ResubmitStudentIdentityApplicationCommand(
        String username,
        String password,
        String realName,
        String studentNumber,
        String grade,
        String className,
        List<String> proofFileKeys
) {
    public ResubmitStudentIdentityApplicationCommand {
        proofFileKeys = proofFileKeys == null ? List.of() : List.copyOf(proofFileKeys);
    }

    @Override
    public String toString() {
        return "ResubmitStudentIdentityApplicationCommand{username='" + username
                + "', password=[REDACTED]}";
    }
}
