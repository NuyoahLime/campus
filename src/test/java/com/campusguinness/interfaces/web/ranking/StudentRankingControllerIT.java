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
class StudentRankingControllerIT extends RankingIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolAdminRankingApplicationService managementService;

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotUseStudentRankingApi() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCannotUseStudentRankingApi() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanListRankings() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].activityProjectId")
                        .value(activityProjectId.toString()));
    }

    @Test
    void invalidPageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .param("page", "-1")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .param("size", "101")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlongKeywordReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .param("keyword", "x".repeat(101))
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAvailabilityReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/student/rankings")
                        .param("rankingAvailability", "HISTORICAL")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCanReadAssignedRanking() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.entries[0].scoreDisplayValue")
                        .value("100"));
    }

    @Test
    void studentCannotReadUnassignedRanking() throws Exception {
        UUID unassigned = createUnassignedProject(schoolId, adminId, "Same");

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        unassigned)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherSchoolRankingReturns404() throws Exception {
        UUID otherSchoolProject = createUnassignedProject(
                otherSchoolId, otherAdminId, "Other");

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        otherSchoolProject)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawnRankingReturns404() throws Exception {
        publish();
        managementService.withdraw(
                adminId, activityProjectId, "withdrawn");

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCanReadOwnRank() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}/mine",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankPosition").value(1))
                .andExpect(jsonPath("$.scoreDisplayValue").value("100"));
    }

    @Test
    void unrankedStudentOwnRankReturns404() throws Exception {
        UUID unranked = createUser("ranking-unranked-" + fixtureSuffix);
        UUID membership = membership(
                unranked, schoolId, "STUDENT", "ACTIVE");
        assignStudent(activityId, activityProjectId, membership, adminId);
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}/mine",
                        activityProjectId)
                        .with(auth(unranked, schoolId, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void responseDoesNotExposeStudentId() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].studentId").doesNotExist());
    }

    @Test
    void responseDoesNotExposeScoreAttemptId() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].scoreAttemptId").doesNotExist());
    }

    @Test
    void responseDoesNotExposeStudentNumber() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
                        activityProjectId)
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].studentNumber").doesNotExist())
                .andExpect(jsonPath("$.entries[0].membershipId").doesNotExist());
    }

    @Test
    void responseMarksCurrentStudentServerSide() throws Exception {
        publish();

        mockMvc.perform(get(
                        "/api/v1/student/rankings/{activityProjectId}",
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
