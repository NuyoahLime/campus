package com.campusguinness.interfaces.web.activityapplication;

import java.util.UUID;

public record ActivityApplicationResponse(UUID id, String status, UUID createdActivityId) {}
