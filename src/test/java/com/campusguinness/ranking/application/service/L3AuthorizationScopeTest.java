package com.campusguinness.ranking.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class L3AuthorizationScopeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unknownOnlyScopeRejected() throws Exception {
        assertThatThrownBy(() -> L3AuthorizationScope.parse(mapper.readTree("""
                {"activityId":"0b7bb29f-30e4-44cb-b1af-c7420473a3a2"}
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported dataScope field: activityId");
    }

    @Test
    void singularGradeFieldRejected() throws Exception {
        assertThatThrownBy(() -> L3AuthorizationScope.parse(mapper.readTree("""
                {"grade":"G5"}
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported dataScope field: grade");
    }

    @Test
    void knownPlusUnknownScopeRejected() throws Exception {
        assertThatThrownBy(() -> L3AuthorizationScope.parse(mapper.readTree("""
                {"grades":["G5"],"unknownKey":"x"}
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported dataScope field: unknownKey");
    }

    @Test
    void otherUnknownFieldRejectedBeforeNormalization() throws Exception {
        assertThatThrownBy(() -> L3AuthorizationScope.parse(mapper.readTree("""
                {"studentIds":["a"],"classNames":["C1"]}
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported dataScope field: studentIds");
    }
}
