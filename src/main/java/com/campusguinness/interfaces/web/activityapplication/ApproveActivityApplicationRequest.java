package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApproveActivityApplicationRequest(
        @NotNull UUID activityId) {}
