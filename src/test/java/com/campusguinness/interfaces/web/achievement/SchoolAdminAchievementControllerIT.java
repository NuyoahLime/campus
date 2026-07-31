package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SchoolAdminAchievementControllerIT
        extends AchievementIntegrationTestSupport {

    @Autowired MockMvc mockMvc;

    @Test
    void unauthenticatedIssueReturns401() throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotIssue() throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version))
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotIssue() throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version))
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCanIssueOwnSchoolEntry() throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version))
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rankingEntryId",
                        is(firstEntryId(version).toString())))
                .andExpect(jsonPath("$.studentId",
                        is(studentId.toString())))
                .andExpect(jsonPath("$.created", is(true)));
    }

    @Test
    void schoolAdminCannotIssueOtherSchoolEntry() throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version))
                        .with(auth(
                                otherAdminId,
                                otherSchoolId,
                                "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminCannotListOtherSchoolRecords() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/school-admin/achievement-records/projects/{id}",
                        activityProjectId)
                        .with(auth(
                                otherAdminId,
                                otherSchoolId,
                                "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestCannotChooseStudentIdIssuedByOrVerificationCode()
            throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(issueRequest(firstEntryId(version))
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "%s",
                                  "issuedBy": "%s",
                                  "verificationCode": "%s",
                                  "rank": 999,
                                  "status": "REVOKED"
                                }
                                """.formatted(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "f".repeat(32))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId", is(studentId.toString())))
                .andExpect(jsonPath("$.issuedBy", is(adminId.toString())))
                .andExpect(jsonPath("$.rankPosition", is(1)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.verificationCode",
                        org.hamcrest.Matchers.not("f".repeat(32))));
    }

    @Test
    void issueReturns201FirstTimeAnd200WhenExisting() throws Exception {
        RankingVersionDetail version = publishRanking();
        UUID entryId = firstEntryId(version);

        String firstBody = mockMvc.perform(issueRequest(entryId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        startsWith(
                                "/api/v1/school-admin/achievement-records/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondBody = mockMvc.perform(issueRequest(entryId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstRecordId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(firstBody).get("recordId").asText();
        String secondRecordId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(secondBody).get("recordId").asText();
        org.assertj.core.api.Assertions.assertThat(secondRecordId)
                .isEqualTo(firstRecordId);
    }

    @Test
    void statusesAreLoadedForTheWholeVersionInOneRequest()
            throws Exception {
        RankingVersionDetail version = publishRanking();

        mockMvc.perform(get(
                        "/api/v1/school-admin/achievement-records/ranking-versions/{id}/statuses",
                        version.versionId())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rankingEntryId",
                        is(firstEntryId(version).toString())))
                .andExpect(jsonPath("$[0].achievementRecordId")
                        .doesNotExist());
    }

    @Test
    void legacyIssueRequiresSuperAdminAndIsIdempotent()
            throws Exception {
        RankingVersionDetail version = publishRanking();
        String body = """
                {"rankingEntryId":"%s"}
                """.formatted(firstEntryId(version));

        mockMvc.perform(post(
                        "/api/v1/activity-projects/{id}/achievement-records",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        UUID superAdmin = createUser(
                "ranking-super-admin-" + fixtureSuffix);
        mockMvc.perform(post(
                        "/api/v1/activity-projects/{id}/achievement-records",
                        activityProjectId)
                        .with(auth(superAdmin, null, "SUPER_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/activity-projects/{id}/achievement-records",
                        activityProjectId)
                        .with(auth(superAdmin, null, "SUPER_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM achievement_records",
                Long.class)).isEqualTo(1);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            issueRequest(UUID entryId) {
        return put(
                "/api/v1/school-admin/achievement-records/ranking-entries/{id}",
                entryId).with(csrf());
    }
}
