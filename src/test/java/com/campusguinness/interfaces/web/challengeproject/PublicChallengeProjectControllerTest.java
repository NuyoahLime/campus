package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.PublicProjectDetailResult;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
import com.campusguinness.project.application.query.model.QueryPage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicChallengeProjectController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class PublicChallengeProjectControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChallengeProjectQueryService queryService;

    @Nested class ListPublic {
        @Test void shouldReturn200() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test void shouldPassKeywordFilter() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects?keyword=math"))
                    .andExpect(status().isOk());
        }

        @Test void shouldPassCategoryFilter() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects?category=MATH"))
                    .andExpect(status().isOk());
        }

        @Test void shouldPassScoreStorageTypeFilter() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects?scoreStorageType=INTEGER"))
                    .andExpect(status().isOk());
        }

        @Test void shouldPassVenueKeywordFilter() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects?venueKeyword=操场"))
                    .andExpect(status().isOk());
        }

        @Test void shouldPassEquipmentKeywordFilter() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
            mvc.perform(get("/api/v1/public/challenge-projects?equipmentKeyword=秒表"))
                    .andExpect(status().isOk());
        }

        @Test void negativePageReturns400() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(-1), eq(20)))
                    .thenThrow(new IllegalArgumentException("page must be >= 0"));
            mvc.perform(get("/api/v1/public/challenge-projects?page=-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test void excessiveSizeReturns400() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(101)))
                    .thenThrow(new IllegalArgumentException("size must be between 1 and 100"));
            mvc.perform(get("/api/v1/public/challenge-projects?size=101"))
                    .andExpect(status().isBadRequest());
        }

        @Test void invalidScoreStorageTypeReturns400() throws Exception {
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenThrow(new IllegalArgumentException("Invalid scoreStorageType: INVALID"));
            mvc.perform(get("/api/v1/public/challenge-projects?scoreStorageType=INVALID"))
                    .andExpect(status().isBadRequest());
        }

        @Test void publicListExcludesInternalFields() throws Exception {
            var result = new ChallengeProjectListResult(
                    UUID.randomUUID(), "测试项目", "MATH", "摘要",
                    "INTEGER", "HIGHER_BETTER", "次", "PUBLISHED",
                    java.time.Instant.now());
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(20)))
                    .thenReturn(new QueryPage<>(java.util.List.of(result), 0, 20, 1));
            mvc.perform(get("/api/v1/public/challenge-projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].projectId").exists())
                    .andExpect(jsonPath("$.items[0].name").value("测试项目"))
                    .andExpect(jsonPath("$.items[0].category").value("MATH"))
                    .andExpect(jsonPath("$.items[0].descriptionSummary").value("摘要"))
                    .andExpect(jsonPath("$.items[0].scoreStorageType").value("INTEGER"))
                    .andExpect(jsonPath("$.items[0].comparisonDirection").value("HIGHER_BETTER"))
                    .andExpect(jsonPath("$.items[0].scoreUnit").value("次"))
                    .andExpect(jsonPath("$.items[0].createdAt").doesNotExist())
                    .andExpect(jsonPath("$.items[0].projectStatus").doesNotExist())
                    .andExpect(jsonPath("$.items[0].version").doesNotExist())
                    .andExpect(jsonPath("$.items[0].description").doesNotExist())
                    .andExpect(jsonPath("$.totalPages").exists())
                    .andExpect(jsonPath("$.hasNext").exists());
        }

        @Test void paginationMetadataPresent() throws Exception {
            var result = new ChallengeProjectListResult(
                    UUID.randomUUID(), "P", "MATH", "S",
                    "INTEGER", "HIGHER_BETTER", null, "PUBLISHED",
                    java.time.Instant.now());
            when(queryService.listPublic(any(PublicProjectListFilter.class), eq(0), eq(10)))
                    .thenReturn(new QueryPage<>(java.util.List.of(result), 0, 10, 25));
            mvc.perform(get("/api/v1/public/challenge-projects?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true));
        }
    }

    @Nested class GetDetail {
        @Test void shouldReturn200ForPublished() throws Exception {
            UUID projectId = UUID.randomUUID();
            var detail = new PublicProjectDetailResult(
                    projectId, "详细项目", "MATH", "完整描述",
                    "需要操场", "需要篮球", "比赛规则",
                    "INTEGER", "NUMERIC", "HIGHER_BETTER",
                    "BEST", false, "次", 2, "一年级,二年级");
            when(queryService.findPublishedById(projectId)).thenReturn(Optional.of(detail));
            mvc.perform(get("/api/v1/public/challenge-projects/" + projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                    .andExpect(jsonPath("$.name").value("详细项目"))
                    .andExpect(jsonPath("$.category").value("MATH"))
                    .andExpect(jsonPath("$.description").value("完整描述"))
                    .andExpect(jsonPath("$.venueRequirements").value("需要操场"))
                    .andExpect(jsonPath("$.equipmentRequirements").value("需要篮球"))
                    .andExpect(jsonPath("$.rulesText").value("比赛规则"))
                    .andExpect(jsonPath("$.scoreStorageType").value("INTEGER"))
                    .andExpect(jsonPath("$.scoreIndicatorType").value("NUMERIC"))
                    .andExpect(jsonPath("$.comparisonDirection").value("HIGHER_BETTER"))
                    .andExpect(jsonPath("$.effectiveScoreRule").value("BEST"))
                    .andExpect(jsonPath("$.allowTie").value(false))
                    .andExpect(jsonPath("$.scoreUnit").value("次"))
                    .andExpect(jsonPath("$.decimalPlaces").value(2))
                    .andExpect(jsonPath("$.gradeOrder").value("一年级,二年级"))
                    .andExpect(jsonPath("$.projectStatus").doesNotExist())
                    .andExpect(jsonPath("$.createdAt").doesNotExist())
                    .andExpect(jsonPath("$.version").doesNotExist());
        }

        @Test void shouldReturn404ForDraftOrArchived() throws Exception {
            UUID projectId = UUID.randomUUID();
            when(queryService.findPublishedById(projectId)).thenReturn(Optional.empty());
            mvc.perform(get("/api/v1/public/challenge-projects/" + projectId))
                    .andExpect(status().isNotFound());
        }

        @Test void shouldReturn404ForNonexistent() throws Exception {
            UUID projectId = UUID.randomUUID();
            when(queryService.findPublishedById(projectId)).thenReturn(Optional.empty());
            mvc.perform(get("/api/v1/public/challenge-projects/" + projectId))
                    .andExpect(status().isNotFound());
        }
    }
}
