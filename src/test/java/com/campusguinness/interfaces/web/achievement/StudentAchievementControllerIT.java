package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.AchievementIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StudentAchievementControllerIT
        extends AchievementIntegrationTestSupport {

    @Autowired MockMvc mockMvc;

    @Test
    void studentListsOwnAchievementRecords() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get("/api/v1/student/achievement-records")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].recordId",
                        is(record.recordId().toString())))
                .andExpect(jsonPath("$.items[0].issuedBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].studentId").doesNotExist());
    }

    @Test
    void statusAndKeywordFiltersWork() throws Exception {
        var version = publishRanking();
        issueFirst(version);
        rankingService.withdraw(adminId, activityProjectId, "correction");

        mockMvc.perform(get("/api/v1/student/achievement-records")
                        .param("status", "REVOKED")
                        .param("keyword", " Activity ")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status", is("REVOKED")));
    }

    @Test
    void detailLoadsOwnRecordAndShowsRevocationReason()
            throws Exception {
        var version = publishRanking();
        var record = issueRecord(version);
        rankingService.withdraw(adminId, activityProjectId, "correction");

        mockMvc.perform(get(
                        "/api/v1/student/achievement-records/{id}",
                        record.recordId())
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVOKED")))
                .andExpect(jsonPath("$.revocationReason", is("correction")))
                .andExpect(jsonPath("$.issuedBy").doesNotExist())
                .andExpect(jsonPath("$.revokedBy").doesNotExist())
                .andExpect(jsonPath("$.studentId").doesNotExist());
    }

    @Test
    void studentCannotReadOtherStudentRecord() throws Exception {
        var record = issueRecord(publishRanking());

        mockMvc.perform(get(
                        "/api/v1/student/achievement-records/{id}",
                        record.recordId())
                        .with(auth(
                                otherAdminId,
                                otherSchoolId,
                                "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedAndTeacherAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/student/achievement-records"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/student/achievement-records")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyStudentApiIsPreserved() throws Exception {
        issueFirst(publishRanking());

        mockMvc.perform(get("/api/v1/achievement-records/mine")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
