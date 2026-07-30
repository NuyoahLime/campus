package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SchoolAdminRankingControllerIT extends RankingIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolAdminRankingApplicationService service;

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotUseRankingManagement() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotUseRankingManagement() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCanListOwnSchoolProjects() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].activityProjectId",
                        is(activityProjectId.toString())));
    }

    @Test
    void schoolAdminCannotPreviewOtherSchoolProject() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/preview",
                        activityProjectId)
                        .with(auth(otherAdminId, otherSchoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminCanPreviewRanking() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/preview",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRanked", is(1)))
                .andExpect(jsonPath("$.entries[0].scoreAttemptId",
                        is(scoreAttemptId.toString())));
    }

    @Test
    void schoolAdminCanPublishEndedProject() throws Exception {
        mockMvc.perform(publishRequest(fingerprint()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionStatus", is("PUBLISHED")));
    }

    @Test
    void pendingReviewBlocksPublish() throws Exception {
        createScore(
                activityProjectId,
                schoolId,
                studentId,
                teacherId,
                "INTEGER",
                BigDecimal.valueOf(90),
                null,
                null,
                "PENDING_REVIEW",
                false,
                Instant.parse("2026-07-30T09:00:00Z"));

        mockMvc.perform(publishRequest("0".repeat(64)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PENDING_REVIEW_SCORES")));
    }

    @Test
    void staleFingerprintReturns409() throws Exception {
        mockMvc.perform(publishRequest("0".repeat(64)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RANKING_SOURCE_CHANGED")));
    }

    @Test
    void publishReturns201AndLocation() throws Exception {
        var result = mockMvc.perform(publishRequest(fingerprint()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.startsWith(
                                "/api/v1/school-admin/rankings/versions/")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("versionId");
    }

    @Test
    void schoolAdminCanGetCurrentVersion() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/current",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber", is(1)));
    }

    @Test
    void schoolAdminCanListVersionHistory() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/versions",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void schoolAdminCanReadHistoricalVersion() throws Exception {
        var version = publish();

        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/versions/{id}",
                        version.versionId())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].scoreAttemptId",
                        is(scoreAttemptId.toString())));
    }

    @Test
    void schoolAdminCanWithdrawCurrentVersion() throws Exception {
        publish();

        mockMvc.perform(post(
                        "/api/v1/school-admin/rankings/projects/{id}/withdraw",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"incorrect score\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void blankWithdrawalReasonReturns400() throws Exception {
        publish();

        mockMvc.perform(post(
                        "/api/v1/school-admin/rankings/projects/{id}/withdraw",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noCurrentVersionWithdrawReturns409() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/school-admin/rankings/projects/{id}/withdraw",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"reason\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("NO_CURRENT_RANKING")));
    }

    @Test
    void withdrawnCurrentReturns404() throws Exception {
        publish();
        service.withdraw(adminId, activityProjectId, "reason");

        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/current",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void oldAdminEndpointCannotCrossSchool() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/activity-projects/{id}/ranking-preview",
                        activityProjectId)
                        .with(auth(otherAdminId, otherSchoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects")
                        .param("page", "-1")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlongKeywordReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/rankings/projects")
                        .param("keyword", "x".repeat(101))
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder publishRequest(
            String fingerprint) {
        return post(
                "/api/v1/school-admin/rankings/projects/{id}/publish",
                activityProjectId)
                .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedSourceFingerprint\":\"" + fingerprint + "\"}");
    }

    private String fingerprint() {
        return service.preview(adminId, activityProjectId).sourceFingerprint();
    }

    private com.campusguinness.ranking.application.query.model.RankingVersionDetail publish() {
        return service.publish(adminId, activityProjectId, fingerprint());
    }
}
