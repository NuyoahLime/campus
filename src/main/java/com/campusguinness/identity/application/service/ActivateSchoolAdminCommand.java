package com.campusguinness.identity.application.service;

public record ActivateSchoolAdminCommand(
        String username,
        String invitationCode,
        String newPassword,
        String confirmPassword
) {}
