package com.campusguinness.activity.application.result;
import java.util.UUID;
public record ActivityResult(UUID id, String executionStatus, String publicStatus) {}
