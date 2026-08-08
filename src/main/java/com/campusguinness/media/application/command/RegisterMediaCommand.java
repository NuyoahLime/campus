package com.campusguinness.media.application.command;
import java.util.UUID;
public record RegisterMediaCommand(UUID schoolId, UUID activityId,
        String fileKey, String fileName, String fileType, String fileFormat,
        long fileSizeBytes, String checksum, String description) {
    public RegisterMediaCommand { if (fileKey == null||fileKey.isBlank()) throw new IllegalArgumentException("fileKey required"); }
}
