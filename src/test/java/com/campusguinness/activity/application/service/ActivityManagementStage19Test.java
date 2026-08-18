package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.command.UpdateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectRepository;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionSnapshot;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.project.internal.domain.ProjectCategory;
import com.campusguinness.project.internal.domain.ProjectName;
import com.campusguinness.project.internal.domain.ProjectStatus;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.project.internal.domain.ScoreIndicatorType;
import com.campusguinness.project.internal.domain.ScoreStorageType;
import com.campusguinness.school.application.query.SchoolOperationalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityManagementStage19Test {

    @Mock ActivityRepository activities;
    @Mock SchoolResourceAuthorization authorization;
    @Mock ActivityProjectRepository activityProjects;
    @Mock ChallengeProjectRepository projects;
    @Mock ProjectRuleVersionRepository ruleVersions;
    @Mock SchoolOperationalQuery schoolOperational;

    private ActivityManagementService service;
    private UUID schoolId;
    private UUID actorId;
    private UUID projectId;
    private UUID ruleVersionId;

    @BeforeEach
    void setUp() {
        service = new ActivityManagementService(activities, authorization, activityProjects,
                projects, ruleVersions, schoolOperational);
        schoolId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        lenient().when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        lenient().when(authorization.requireSchoolAdmin(schoolId)).thenReturn(actorId);
        lenient().when(schoolOperational.isNormal(schoolId)).thenReturn(true);
    }

    @Test
    void createDerivesSchoolAndBindsCurrentRuleVersion() {
        var project = project(ProjectStatus.PUBLISHED, ruleVersionId);
        var snapshot = ruleVersion(ruleVersionId, projectId, 1);
        when(projects.findById(new ChallengeProjectId(projectId))).thenReturn(Optional.of(project));
        when(ruleVersions.findAllByProjectId(projectId)).thenReturn(List.of(snapshot));

        var result = service.create(new CreateActivityCommand(projectId, "校内跳绳活动", "说明",
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-01T09:00:00Z"), "操场"));

        assertThat(result.executionStatus()).isEqualTo("DRAFT");
        var activityCaptor = ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().schoolId()).isEqualTo(schoolId);
        assertThat(activityCaptor.getValue().createdBy()).isEqualTo(actorId);

        var snapshotCaptor = ArgumentCaptor.forClass(ActivityProjectRepository.ActivityProjectSnapshot.class);
        verify(activityProjects).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().activityId()).isEqualTo(activityCaptor.getValue().id().value());
        assertThat(snapshotCaptor.getValue().projectId()).isEqualTo(projectId);
        assertThat(snapshotCaptor.getValue().ruleVersionId()).isEqualTo(ruleVersionId);
    }

    @Test
    void createRejectsUnavailableProjectBeforeActivityPersistence() {
        when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(ProjectStatus.ARCHIVED, ruleVersionId)));

        assertThatThrownBy(() -> service.create(new CreateActivityCommand(projectId, "活动", null,
                null, null, null)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting("code").isEqualTo("ACTIVITY_PROJECT_UNAVAILABLE");
        verify(activities, never()).save(any());
        verify(activityProjects, never()).save(any());
    }

    @Test
    void createRejectsNonOperationalSchoolBeforeProjectLookup() {
        when(schoolOperational.isNormal(schoolId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateActivityCommand(projectId, "活动", null,
                null, null, null)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting("code").isEqualTo("SCHOOL_NOT_OPERATIONAL");
        verify(projects, never()).findById(any());
        verify(activities, never()).save(any());
    }

    @Test
    void publishUsesActivitySchoolAndSecondPublishIsRejectedByDomain() {
        var activity = Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                .schoolId(schoolId).createdBy(actorId).title("活动"));
        when(activities.findById(activity.id())).thenReturn(Optional.of(activity));

        assertThat(service.publish(activity.id().value()).executionStatus()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.publish(activity.id().value()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot publish from execution status PUBLISHED");
        verify(authorization, org.mockito.Mockito.times(2)).requireSchoolAdmin(schoolId);
    }

    @Test
    void crossSchoolActivityIsRejectedByServerScope() {
        var otherSchool = UUID.randomUUID();
        var activity = Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                .schoolId(otherSchool).createdBy(actorId).title("活动"));
        when(activities.findById(activity.id())).thenReturn(Optional.of(activity));
        when(authorization.requireSchoolAdmin(otherSchool)).thenThrow(
                new IdentityApplicationException("SCHOOL_ADMIN_SCOPE_DENIED", "denied"));

        assertThatThrownBy(() -> service.update(activity.id().value(),
                new UpdateActivityCommand("新标题", null, null, null, null)))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting("code").isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED");
        verify(activities, never()).save(any());
    }

    private ChallengeProject project(ProjectStatus status, UUID currentRuleVersionId) {
        return ChallengeProject.reconstitute(new ChallengeProjectId(projectId),
                new ProjectName("一分钟跳绳"), new ProjectCategory("ATHLETICS"), scoreConfig(),
                "说明", null, null, status, currentRuleVersionId);
    }

    private ProjectRuleVersionSnapshot ruleVersion(UUID id, UUID projectId, int number) {
        return new ProjectRuleVersionSnapshot(id, projectId, number, scoreConfig(), null, null,
                "初始规则", actorId, Instant.now());
    }

    private ScoreConfig scoreConfig() {
        return new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                ComparisonDirection.HIGHER_BETTER, "次", null, "BEST", null,
                "一分钟内完成次数", true);
    }
}
