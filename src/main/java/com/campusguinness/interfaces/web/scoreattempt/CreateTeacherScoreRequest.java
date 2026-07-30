package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.application.command.CreateTeacherScoreCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CreateTeacherScoreRequest extends TeacherScoreValueRequest {
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

    public CreateTeacherScoreCommand toCommand() {
        return new CreateTeacherScoreCommand(
                activityProjectId,
                studentId,
                getIntegerValue(),
                getDecimalValue(),
                getDurationMs(),
                getGrade(),
                getScoreBusinessTime(),
                getTimeSource());
    }
}
