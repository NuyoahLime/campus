package com.campusguinness.result.application.format;

import java.time.Instant;
import java.util.UUID;

public record ResultFormatEditResult(UUID formatEditId, UUID resultId, UUID resultVersionId,
                                     int revision, ResultFormatPresentation presentation,
                                     String reason, Instant editedAt) {}
