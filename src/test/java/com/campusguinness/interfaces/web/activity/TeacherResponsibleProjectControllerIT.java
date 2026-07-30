package com.campusguinness.interfaces.web.activity;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TeacherResponsibleProjectControllerIT
        extends ScoreEntryIntegrationTestSupport {
    @Autowired MockMvc mvc;

    private UUID responsibleAssignmentId;

    @BeforeEach
    void assignResponsibleTeacher() {
        UUID membershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='TEACHER'
                """, UUID.class, teacherId, schoolId);
        responsibleAssignmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO responsible_teachers(
                  id,activity_project_id,teacher_membership_id,created_at)
                VALUES (?,?,?,?)
                """, responsibleAssignmentId, activityProjectId,
                membershipId, ts(Instant.now()));
    }

    @AfterEach
    void removeResponsibleTeacher() {
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE id=?",
                responsibleAssignmentId);
    }

    @Test
    void unauthenticatedProjectListReturns401() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotUseTeacherApi() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCannotUseTeacherApi() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanListResponsibleProjects() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].activityProjectId")
                        .value(activityProjectId.toString()))
                .andExpect(jsonPath("$.items[0].schoolId")
                        .value(schoolId.toString()));
    }

    @Test
    void teacherCannotReadUnassignedProject() throws Exception {
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE id=?",
                responsibleAssignmentId);

        mvc.perform(get("/api/v1/teacher/responsible-projects/"
                        + activityProjectId)
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCanListAssignedParticipants() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects/"
                        + activityProjectId + "/participants")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].studentId")
                        .value(studentId.toString()));
    }

    @Test
    void overlongKeywordReturns400() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .param("keyword", "x".repeat(101))
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPaginationReturns400() throws Exception {
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .param("page", "-1")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teacher/responsible-projects")
                        .param("size", "101")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isBadRequest());
    }
}
