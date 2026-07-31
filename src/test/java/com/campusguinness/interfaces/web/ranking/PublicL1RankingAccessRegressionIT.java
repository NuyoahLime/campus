package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicL1RankingAccessRegressionIT extends RankingIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolAdminRankingApplicationService managementService;

    @Test
    void guestCannotReadPublishedL1Ranking() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/public/activity-projects/{id}/ranking",
                        activityProjectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedStudentCannotUsePublicPathToBypassAssignment()
            throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/public/activity-projects/{id}/ranking",
                        activityProjectId)
                        .with(auth(UUID.randomUUID(), schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicPathReturns404WithoutLeakingMetadata() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/public/activity-projects/{id}/ranking",
                        activityProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ranking not found"))
                .andExpect(jsonPath("$.schoolName").doesNotExist())
                .andExpect(jsonPath("$.activityTitle").doesNotExist())
                .andExpect(jsonPath("$.totalRanked").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void withdrawnL1AlsoReturns404() throws Exception {
        publish();
        managementService.withdraw(
                adminId, activityProjectId, "withdrawn");

        mockMvc.perform(get(
                        "/api/v1/public/activity-projects/{id}/ranking",
                        activityProjectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void noPublicL3RouteIsIntroduced() throws Exception {
        mockMvc.perform(get("/api/v1/public/rankings"))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminCurrentRankingApiStillWorks() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/school-admin/rankings/projects/{id}/current",
                        activityProjectId)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    void studentAssignedRankingApiStillWorks() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{id}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].isCurrentStudent").value(true));
    }

    private void publish() {
        String fingerprint = managementService.preview(
                adminId, activityProjectId).sourceFingerprint();
        managementService.publish(
                adminId, activityProjectId, fingerprint);
    }
}
