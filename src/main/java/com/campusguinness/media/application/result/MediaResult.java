package com.campusguinness.media.application.result;
import java.util.UUID;
public record MediaResult(UUID id, String internalStatus, String publicStatus) {}
