package com.campusguinness.school.application.command;

public record SubmitSchoolRegistrationCommand(
        String schoolName, String unifiedCodeType, String unifiedCode,
        String schoolType, String region, String address,
        String contactName, String contactPhone, String contactEmail,
        String description, String evidenceFileKey) {
    public SubmitSchoolRegistrationCommand {
        if (schoolName == null || schoolName.isBlank()) throw new IllegalArgumentException("schoolName required");
        if (unifiedCodeType == null || unifiedCodeType.isBlank()) throw new IllegalArgumentException("unifiedCodeType required");
    }
}
