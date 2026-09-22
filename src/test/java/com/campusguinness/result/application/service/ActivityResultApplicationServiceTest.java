package com.campusguinness.result.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.activity.internal.domain.PublicStatus;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.media.application.service.MediaEligibilityValidator;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityResultApplicationServiceTest {
    @Mock ActivityResultRepository activityResults;
    @Mock ResultVersionRepository resultVersions;
    @Mock ActivityRepository activities;
    @Mock SchoolResourceAuthorization authorization;
    @Mock MediaEligibilityValidator mediaEligibilityValidator;

    ActivityResultApplicationService svc;

    @BeforeEach
    void setUp() {
        svc = new ActivityResultApplicationService(
                activityResults, resultVersions, activities, authorization,
                mediaEligibilityValidator, new ObjectMapper());
    }

    @Test
    void readEditorWithoutResultDoesNotCreateRows() {
        Activity activity = activity(ExecutionStatus.PUBLISHED);
        when(activities.findById(activity.id())).thenReturn(Optional.of(activity));
        when(activityResults.findByActivityId(activity.id().value())).thenReturn(Optional.empty());

        var result = svc.readEditor(activity.id().value());

        assertThat(result.resultId()).isNull();
        assertThat(result.currentCandidateVersionId()).isNull();
        assertThat(result.candidateContent()).isNull();
        verify(activityResults, never()).save(any());
        verify(resultVersions, never()).create(any());
    }

    @Test
    void firstSaveCreatesLazyResultAndCandidateVersion() {
        Activity activity = activity(ExecutionStatus.PUBLISHED);
        when(activities.findById(activity.id())).thenReturn(Optional.of(activity));
        when(activityResults.findByActivityId(activity.id().value())).thenReturn(Optional.empty());

        var result = svc.saveEditorContent(activity.id().value(),
                new SaveActivityResultContentCommand(" Title ", " Summary ", List.of(" rank "), List.of()));

        assertThat(result.resultId()).isNotNull();
        assertThat(result.currentCandidateVersionId()).isNotNull();
        assertThat(result.currentInternalVersionId()).isNull();
        assertThat(result.currentPublicVersionId()).isNull();
        assertThat(result.candidateContent().title()).isEqualTo("Title");
        assertThat(result.candidateContent().scoreHighlights()).containsExactly("rank");
        verify(activityResults).findByActivityId(activity.id().value());
        verify(resultVersions).create(any());
        verify(activityResults, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void draftActivitySaveDeniedBeforeCreation() {
        Activity activity = activity(ExecutionStatus.DRAFT);
        when(activities.findById(activity.id())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> svc.saveEditorContent(activity.id().value(),
                new SaveActivityResultContentCommand("Title", "Summary", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
        verify(activityResults, never()).save(any());
        verify(resultVersions, never()).create(any());
    }

    @Test
    void publishInternalUsesExactCandidateAndStampsIt() {
        ActivityResult result = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .activityId(UUID.randomUUID()));
        UUID candidateId = UUID.randomUUID();
        result.createFirstCandidate(candidateId);
        Activity activity = Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(result.activityId()))
                .schoolId(result.schoolId())
                .title("Activity")
                .createdBy(UUID.randomUUID())
                .executionStatus(ExecutionStatus.PUBLISHED)
                .publicStatus(PublicStatus.NOT_SUBMITTED));
        ResultVersion candidate = ResultVersion.create(new ResultVersion.Builder()
                .id(new ResultVersionId(candidateId))
                .resultId(result.id())
                .versionNumber(1)
                .title("V1")
                .summaryText("Summary")
                .scoreHighlights("[]")
                .mediaRefs("[]"));
        when(activityResults.findById(result.id())).thenReturn(Optional.of(result));
        when(activities.findById(new ActivityId(result.activityId()))).thenReturn(Optional.of(activity));
        when(resultVersions.findById(candidate.id())).thenReturn(Optional.of(candidate));

        var published = svc.publishInternal(result.id().value());

        assertThat(published.internalStatus()).isEqualTo("INTERNAL_PUBLISHED");
        ArgumentCaptor<ActivityResult> saved = ArgumentCaptor.forClass(ActivityResult.class);
        verify(activityResults).save(saved.capture());
        assertThat(saved.getValue().currentInternalVersionId()).isEqualTo(candidateId);
        assertThat(saved.getValue().currentCandidateVersionId()).isEqualTo(candidateId);
        verify(resultVersions).markPublishedInternally(org.mockito.Mockito.eq(candidate.id()), any());
    }

    @Test
    void shouldThrowWhenNotFound() {
        when(activityResults.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.publishInternal(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    private Activity activity(ExecutionStatus status) {
        return Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .title("Activity")
                .createdBy(UUID.randomUUID())
                .executionStatus(status)
                .publicStatus(PublicStatus.NOT_SUBMITTED));
    }
}
