package com.campusguinness.interfaces.web.activity;

import java.util.UUID;

public record ActivityResponse(UUID activityId, String executionStatus, String publicStatus) {}
