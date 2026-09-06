package com.campusguinness.interfaces.web.l3authorization;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateL3AuthorizationRequest(
        JsonNode dataScope,
        Boolean allowSchoolName,
        Boolean allowStudentName) {
}
