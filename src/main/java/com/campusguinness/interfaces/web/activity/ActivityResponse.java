package com.campusguinness.interfaces.web.activity;

import java.util.UUID;

public record ActivityResponse(UUID id, String executionStatus, String publicStatus) {}
