package com.campusguinness.result.application.query.model;

import com.campusguinness.result.application.format.ResultFormatPresentation;

import java.util.UUID;

public record ResultFormatOverlayProjection(
        ResultFormatPresentation presentation,
        UUID currentFormatEditRecordId,
        Integer currentFormatRevision) {
}
