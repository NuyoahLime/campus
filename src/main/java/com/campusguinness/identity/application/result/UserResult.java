package com.campusguinness.identity.application.result;
import java.util.UUID;
public record UserResult(UUID id, String username, String status) {}
