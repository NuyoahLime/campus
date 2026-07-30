package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.application.command.UpdateSchoolAdminScoreDraftCommand;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class SchoolAdminScoreDraftValueRequest {
    @PositiveOrZero
    private Long integerValue;
    private BigDecimal decimalValue;
    @PositiveOrZero
    private Long durationMs;
    @Size(max = 32)
    private String grade;
    @NotNull
    private Instant scoreBusinessTime;
    @NotBlank
    @Size(max = 32)
    private String timeSource;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public Long getIntegerValue() {
        return integerValue;
    }

    public void setIntegerValue(Long integerValue) {
        this.integerValue = integerValue;
    }

    public BigDecimal getDecimalValue() {
        return decimalValue;
    }

    public void setDecimalValue(BigDecimal decimalValue) {
        this.decimalValue = decimalValue;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Instant getScoreBusinessTime() {
        return scoreBusinessTime;
    }

    public void setScoreBusinessTime(Instant scoreBusinessTime) {
        this.scoreBusinessTime = scoreBusinessTime;
    }

    public String getTimeSource() {
        return timeSource;
    }

    public void setTimeSource(String timeSource) {
        this.timeSource = timeSource;
    }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    public void assertNoUnknownFields() {
        if (!unknownFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported request field: " + unknownFields.keySet().iterator().next());
        }
    }

    public UpdateSchoolAdminScoreDraftCommand toUpdateCommand() {
        return new UpdateSchoolAdminScoreDraftCommand(
                integerValue, decimalValue, durationMs, grade,
                scoreBusinessTime, timeSource);
    }
}
