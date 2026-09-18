package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.result.ActivityResultEditorResult;
import com.campusguinness.result.application.query.ActivityResultReadQueryService;
import com.campusguinness.result.application.query.model.ManagementActivityResultDetail;
import com.campusguinness.result.application.service.ActivityResultApplicationService;
import com.campusguinness.result.application.service.ActivityResultPublicationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityResultController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityResultControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityResultApplicationService service;
    @MockitoBean ActivityResultPublicationApplicationService publicationService;
    @MockitoBean ActivityResultReadQueryService readQueryService;

    @Test void publishReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.publishInternal(id)).thenReturn(new ActivityResultResult(id, "INTERNAL_PUBLISHED", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/activity-results/" + id + "/publish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("INTERNAL_PUBLISHED"));
    }
    @Test void readEditorReturns200() throws Exception {
        UUID activityId = UUID.randomUUID();
        when(readQueryService.managementDetail(activityId)).thenReturn(detail(activityId, null));
        mvc.perform(get("/api/v1/activities/" + activityId + "/result"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activityId").value(activityId.toString()));
    }
    @Test void saveEditorReturns200() throws Exception {
        UUID activityId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        when(service.saveEditorContent(org.mockito.Mockito.eq(activityId), any())).thenReturn(new ActivityResultEditorResult(
                activityId, resultId, "DRAFT", "NOT_SUBMITTED", UUID.randomUUID(), null, null, false, null));
        when(readQueryService.managementDetail(activityId)).thenReturn(detail(activityId, resultId));
        mvc.perform(put("/api/v1/activities/" + activityId + "/result")
                        .contentType("application/json")
                        .content("{\"title\":\"T\",\"summaryText\":\"S\",\"scoreHighlights\":[],\"mediaRefs\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultId").value(resultId.toString()));
    }
    @Test void withdrawReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.withdrawInternal(id)).thenReturn(new ActivityResultResult(id, "INTERNAL_WITHDRAWN", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/activity-results/" + id + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("INTERNAL_WITHDRAWN"));
    }
    @Test void returnToDraftReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.returnToDraft(id)).thenReturn(new ActivityResultResult(id, "DRAFT", "PLATFORM_TAKEDOWN"));
        mvc.perform(post("/api/v1/activity-results/" + id + "/return-to-draft"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("DRAFT"));
    }
    @Test void makePublicReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(publicationService.makePublic(id))
                .thenReturn(new ActivityResultResult(id, "INTERNAL_PUBLISHED", "PUBLIC"));
        mvc.perform(post("/api/v1/activity-results/" + id + "/make-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PUBLIC"));
    }
    @Test void notFoundReturns404() throws Exception {
        when(service.publishInternal(any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/activity-results/" + UUID.randomUUID() + "/publish"))
                .andExpect(status().isNotFound());
    }

    private ManagementActivityResultDetail detail(UUID activityId, UUID resultId) {
        return new ManagementActivityResultDetail(
                activityId, resultId, "DRAFT", "NOT_SUBMITTED",
                null, null, null, false, null, null, null);
    }
}
