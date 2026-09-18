package com.campusguinness.interfaces.web.activityresult;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityResultReadAuthorizationIT extends ActivityResultReadTestSupport {
    @Test
    void managementListAndHistoryAreSchoolAdminOnly() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "authorization", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);

        mvc.perform(get("/api/v1/school-admin/activity-results"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/school-admin/activity-results").with(studentA()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/school-admin/activity-results").with(superAdmin()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/school-admin/activity-results").with(adminA()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/school-admin/activities/{activityId}/result/history", fixture.activityId())
                        .with(studentA()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/school-admin/activities/{activityId}/result/history", fixture.activityId())
                        .with(superAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorDetailIsSameSchoolSchoolAdminOnlyAndStudentEndpointIsStudentOnly() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "endpoint-boundary", "PUBLISHED", "DRAFT",
                "NOT_SUBMITTED", false, 1, null, null);

        mvc.perform(get("/api/v1/activities/{activityId}/result", fixture.activityId())
                        .with(adminB()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/activities/{activityId}/result", fixture.activityId())
                        .with(studentA()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/activities/{activityId}/result", fixture.activityId())
                        .with(superAdmin()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/student/activities/{activityId}/result", fixture.activityId())
                        .with(adminA()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/activities/{activityId}/result", fixture.activityId())
                        .with(superAdmin()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/activities/{activityId}/result", fixture.activityId())
                        .with(studentB()))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicEndpointUsesPublicProjectionForAnonymousAndAuthenticatedUsers() throws Exception {
        Fixture fixture = insertResult(
                schoolA, "public-auth", "PUBLISHED", "INTERNAL_PUBLISHED",
                "PUBLIC", false, null, 1, 1);

        mvc.perform(get("/api/v1/public/activities/{activityId}/result", fixture.activityId()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/activities/{activityId}/result", fixture.activityId())
                        .with(adminB()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/activities/{activityId}/result", fixture.activityId())
                        .with(studentB()))
                .andExpect(status().isOk());
    }

    @Test
    void unknownResourcesAre404AcrossReadSurfaces() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/v1/public/activities/{activityId}/result", unknown))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/activities/{activityId}/result", unknown).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/activities/{activityId}/result", unknown).with(adminA()))
                .andExpect(status().isNotFound());
    }
}
