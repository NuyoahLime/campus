package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicAchievementVerificationControllerIT
        extends AchievementIntegrationTestSupport {

    @Autowired MockMvc mockMvc;

    @Test
    void activeRecordVerifiesAsValid() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        record.verificationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void revokedRecordVerifiesAsInvalidButStillReturns200()
            throws Exception {
        var version = publishRanking();
        var record = issueRecord(version);
        rankingService.withdraw(adminId, activityProjectId, "correction");

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        record.verificationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.status", is("REVOKED")))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
    }

    @Test
    void unknownAndMalformedCodesReturnSame404Shape()
            throws Exception {
        String unknownBody = mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        "f".repeat(32)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String malformedBody = mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        "not-a-code"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        org.assertj.core.api.Assertions.assertThat(
                        mapper.readTree(unknownBody).get("message").asText())
                .isEqualTo(
                        mapper.readTree(malformedBody).get("message").asText());
    }

    @Test
    void uppercaseCodeIsNormalized() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        record.verificationCode().toUpperCase(
                                java.util.Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        " " + record.verificationCode() + " "))
                .andExpect(status().isOk());
    }

    @Test
    void publicResponseDoesNotExposeIdentityOrAuditFields()
            throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/{code}",
                        record.verificationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.studentDisplayName").doesNotExist())
                .andExpect(jsonPath("$.studentNumber").doesNotExist())
                .andExpect(jsonPath("$.issuedBy").doesNotExist())
                .andExpect(jsonPath("$.revokedBy").doesNotExist())
                .andExpect(jsonPath("$.revocationReason").doesNotExist());
    }

    @Test
    void publicResponseUsesSnapshotsAndDoesNotReadCurrentRanking()
            throws Exception {
        var version = publishRanking();
        var record = issueRecord(version);
        String originalActivity = version.activityTitle();
        try {
            jdbc.update(
                    "UPDATE activities SET title='Changed Activity' WHERE id=?",
                    activityId);
            publishRanking();

            mockMvc.perform(get(
                            "/api/v1/public/achievement-records/{code}",
                            record.verificationCode()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activityTitle",
                            is(originalActivity)))
                    .andExpect(jsonPath("$.rankingVersionNumber", is(1)))
                    .andExpect(jsonPath("$.rankPosition", is(1)))
                    .andExpect(jsonPath("$.scoreDisplayValue", is("100")));
        } finally {
            jdbc.update(
                    "UPDATE activities SET title=? WHERE id=?",
                    originalActivity,
                    activityId);
        }
    }

    @Test
    void semanticVerifyAliasIsAvailable() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/public/achievement-records/verify/{code}",
                        record.verificationCode()))
                .andExpect(status().isOk());
    }
}
