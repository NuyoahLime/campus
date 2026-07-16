package com.campusguinness.interfaces.web.media;

import java.util.UUID;

public record MediaResponse(UUID id, String internalStatus, String publicStatus, String fileName) {}
