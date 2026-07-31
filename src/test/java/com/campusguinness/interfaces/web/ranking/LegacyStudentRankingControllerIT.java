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
class LegacyStudentRankingControllerIT extends RankingIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolAdminRankingApplicationService managementService;

    @Test
    void legacyStudentRankingPathStillExists() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void legacyPathRequiresStudentRole() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking",
                        activityProjectId)
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyPathRequiresProjectAssignment() throws Exception {
        UUID unassigned = createUnassignedProject(
                schoolId, adminId, "Unassigned");

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking",
                        unassigned)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyPathCannotReadOtherSchoolRanking() throws Exception {
        UUID otherSchoolProject = createUnassignedProject(
                otherSchoolId, otherAdminId, "Other");

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking",
                        otherSchoolProject)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyMinePathReturnsOwnRank() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking/mine",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.scoreValue").value("100"));
    }

    @Test
    void legacyWithdrawnRankingReturns404() throws Exception {
        publish();
        managementService.withdraw(
                adminId, activityProjectId, "withdrawn");

        mockMvc.perform(get(
                        "/api/v1/student/activity-projects/{id}/ranking",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    private void publish() {
        String fingerprint = managementService.preview(
                adminId, activityProjectId).sourceFingerprint();
        managementService.publish(
                adminId, activityProjectId, fingerprint);
    }

    private UUID createUnassignedProject(
            UUID school, UUID creator, String label) {
        UUID activity = createActivity(
                school,
                creator,
                "Ranking Activity " + fixtureSuffix + " " + label,
                "ENDED");
        return attachProject(activity, projectId, ruleVersionId);
    }
}
