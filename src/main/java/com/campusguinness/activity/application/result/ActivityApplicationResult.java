package com.campusguinness.activity.application.result;
import java.util.UUID;
public record ActivityApplicationResult(UUID id, String status, UUID createdActivityId) {}
