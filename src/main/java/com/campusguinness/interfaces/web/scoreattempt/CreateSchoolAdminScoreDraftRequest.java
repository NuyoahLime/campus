package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.application.command.CreateSchoolAdminScoreDraftCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CreateSchoolAdminScoreDraftRequest extends SchoolAdminScoreDraftValueRequest {
    @NotNull
    private UUID activityProjectId;
    @NotNull
    private UUID studentId;

    public UUID getActivityProjectId() {
        return activityProjectId;
    }

    public void setActivityProjectId(UUID activityProjectId) {
        this.activityProjectId = activityProjectId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public CreateSchoolAdminScoreDraftCommand toCommand() {
        return new CreateSchoolAdminScoreDraftCommand(
                activityProjectId, studentId,
                getIntegerValue(), getDecimalValue(), getDurationMs(), getGrade(),
                getScoreBusinessTime(), getTimeSource());
    }
}
