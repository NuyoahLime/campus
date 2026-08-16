package com.campusguinness.interfaces.web.school;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.SchoolAdminGovernanceQueryService;
import com.campusguinness.school.application.query.model.SchoolAdminAccountResult;
import com.campusguinness.school.application.query.model.SchoolAdminInvitationQueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchoolAdminGovernanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchoolAdminGovernanceControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SchoolAdminGovernanceQueryService queryService;

    @Test
    void accountListContainsNoCredentialFields() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(queryService.listSchoolAdmins(schoolId)).thenReturn(List.of(
                new SchoolAdminAccountResult(
                        userId, "school-admin", "NORMAL", "ACTIVE", Instant.now(), null
                )
        ));

        mvc.perform(get("/api/v1/schools/{schoolId}/school-admins", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].loginFailures").doesNotExist());
    }

    @Test
    void invitationListAndDetailNeverExposeSecretFields() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        var invitation = invitation(schoolId, invitationId);
        when(queryService.listInvitations(schoolId, 0, 20, "PENDING"))
                .thenReturn(new QueryPage<>(List.of(invitation), 0, 20, 1));
        when(queryService.invitationDetail(schoolId, invitationId)).thenReturn(invitation);

        mvc.perform(get("/api/v1/schools/{schoolId}/school-admin-invitations", schoolId)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.items[0].expired").value(false))
                .andExpect(jsonPath("$.items[0].invitationCodeHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].invitationCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

        mvc.perform(get(
                        "/api/v1/schools/{schoolId}/school-admin-invitations/{invitationId}",
                        schoolId,
                        invitationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.invitationCodeHash").doesNotExist())
                .andExpect(jsonPath("$.invitationCode").doesNotExist());
    }

    private SchoolAdminInvitationQueryResult invitation(UUID schoolId, UUID invitationId) {
        return new SchoolAdminInvitationQueryResult(
                invitationId, UUID.randomUUID(), "invited-admin", schoolId, "PENDING",
                Instant.now().plusSeconds(3600), null, null, Instant.now(), false
        );
    }
}
