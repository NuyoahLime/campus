package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.format.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResultFormatEditController.class)
@Import(ResultFormatEditControllerTest.MethodSecurity.class)
class ResultFormatEditControllerTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockitoBean ResultFormatEditApplicationService service;
    UUID result=UUID.randomUUID(), version=UUID.randomUUID(), edit=UUID.randomUUID();

    @Test @WithMockUser(roles="SCHOOL_ADMIN") void postSameSchoolReturnsContractWithoutEditorLeak() throws Exception {
        when(service.append(eq(result),eq(version),anyString(),any())).thenReturn(response());
        mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.presentation").exists())
                .andExpect(jsonPath("$.editedBy").doesNotExist()).andExpect(jsonPath("$.formatHistory").doesNotExist());
    }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void postCrossSchoolIs404() throws Exception { when(service.append(any(),any(),any(),any())).thenThrow(new ResultFormatEditException("ACTIVITY_RESULT_FORMAT_NOT_FOUND","not found")); mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid())).andExpect(status().isNotFound()); }
    @Test @WithMockUser(roles="STUDENT") void postStudentDenied() throws Exception { mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid())).andExpect(status().isForbidden()); }
    @Test @WithMockUser(roles="SUPER_ADMIN") void postSuperAdminDenied() throws Exception { mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid())).andExpect(status().isForbidden()); }
    @Test void postAnonymousDenied() throws Exception { mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid())).andExpect(status().isUnauthorized()); }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void historySameSchoolReturnsEditorAudit() throws Exception { when(service.history(result,version)).thenReturn(List.of(new ResultFormatHistoryEntry(edit,result,version,1,presentation(),"r",UUID.randomUUID(),Instant.now()))); mvc.perform(get(historyUrl())).andExpect(status().isOk()).andExpect(jsonPath("$[0].revision").value(1)).andExpect(jsonPath("$[0].editedBy").exists()); }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void historyCrossSchoolIs404() throws Exception { when(service.history(result,version)).thenThrow(new ResultFormatEditException("ACTIVITY_RESULT_FORMAT_NOT_FOUND","not found")); mvc.perform(get(historyUrl())).andExpect(status().isNotFound()); }
    @Test @WithMockUser(roles="STUDENT") void historyStudentDenied() throws Exception { mvc.perform(get(historyUrl())).andExpect(status().isForbidden()); }
    @Test @WithMockUser(roles="SUPER_ADMIN") void historySuperAdminDenied() throws Exception { mvc.perform(get(historyUrl())).andExpect(status().isForbidden()); }
    @Test void historyAnonymousDenied() throws Exception { mvc.perform(get(historyUrl())).andExpect(status().isUnauthorized()); }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void unknownTopLevelJsonRejected() throws Exception { mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content("{\"evil\":1,"+valid().substring(1))).andExpect(status().isBadRequest()); }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void unknownParagraphJsonRejected() throws Exception { invalidFromService(); mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid().replace("\"style\":\"NORMAL\"","\"style\":\"NORMAL\",\"evil\":1"))).andExpect(status().isBadRequest()); }
    @Test @WithMockUser(roles="SCHOOL_ADMIN") void unknownEmphasisJsonRejected() throws Exception { invalidFromService(); mvc.perform(post(postUrl()).with(csrf()).contentType("application/json").content(valid().replace("\"style\":\"BOLD\"","\"style\":\"BOLD\",\"evil\":1"))).andExpect(status().isBadRequest()); }
    private void invalidFromService(){ when(service.append(any(),any(),any(),any(JsonNode.class))).thenThrow(new ResultFormatEditException("ACTIVITY_RESULT_FORMAT_INVALID","unknown key")); }
    private ResultFormatEditResult response(){return new ResultFormatEditResult(edit,result,version,1,presentation(),"r",Instant.now());}
    private ResultFormatPresentation presentation(){return new ResultFormatPresentation(List.of(new ResultFormatPresentation.Paragraph(0,3,"NORMAL")),List.of(new ResultFormatPresentation.EmphasisRange(0,1,"BOLD")));}
    private String postUrl(){return "/api/v1/activity-results/"+result+"/versions/"+version+"/format-edits";}
    private String historyUrl(){return "/api/v1/school-admin/activity-results/"+result+"/versions/"+version+"/format-history";}
    private String valid(){return "{\"reason\":\"r\",\"presentation\":{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":1,\"style\":\"BOLD\"}]}}";}
}
