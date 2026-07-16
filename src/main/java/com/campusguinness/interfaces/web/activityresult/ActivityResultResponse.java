package com.campusguinness.interfaces.web.activityresult;

import java.util.UUID;

public record ActivityResultResponse(UUID id, String internalStatus, String publicStatus) {}
