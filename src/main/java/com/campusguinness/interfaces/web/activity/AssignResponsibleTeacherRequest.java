package com.campusguinness.interfaces.web.activity;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignResponsibleTeacherRequest(@NotNull UUID teacherId) {}
