package com.campusguinness.interfaces.web.activityresult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAnySetter;
@JsonIgnoreProperties(ignoreUnknown = false)
public record ResultFormatEditRequest(String reason, JsonNode presentation) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown request field: " + name);
    }
}
