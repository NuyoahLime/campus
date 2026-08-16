package com.campusguinness.project.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.command.UpdateChallengeProjectCommand;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeProjectLifecycleServiceTest {

    @Mock ChallengeProjectRepository projects;
    @Mock ProjectRuleVersionRepository ruleVersions;
    @Mock PlatformGovernanceAuthorization authorization;
    @Mock AuditRecordCommandPort audit;
    ChallengeProjectApplicationService service;
    UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        when(authorization.requireSuperAdmin()).thenReturn(actorId);
        service = new ChallengeProjectApplicationService(
                projects, ruleVersions, authorization, audit, new ObjectMapper());
    }

    @Test
    void firstPublishCreatesVersionOneAndPointsProjectToIt() {
        ChallengeProject project = draft();
        when(projects.findById(project.id())).thenReturn(Optional.of(project));
        when(ruleVersions.nextVersionNumber(project.id().value())).thenReturn(1);

        service.publish(project.id().value(), "  Initial public release  ");

        var snapshot = ArgumentCaptor.forClass(ProjectRuleVersionSnapshot.class);
        verify(ruleVersions).save(snapshot.capture());
        assertThat(snapshot.getValue().versionNumber()).isEqualTo(1);
        assertThat(snapshot.getValue().createdBy()).isEqualTo(actorId);
        assertThat(snapshot.getValue().scoreConfig().allowTie()).isFalse();
        assertThat(project.status()).isEqualTo(ProjectStatus.PUBLISHED);
        assertThat(project.currentRuleVersionId()).isEqualTo(snapshot.getValue().id());

        var auditRecord = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(audit).record(auditRecord.capture());
        assertThat(auditRecord.getValue().detail()).contains("\"reason\":\"Initial public release\"");
    }

    @Test
    void editingPublishedRulesCreatesVersionTwo() {
        ChallengeProject project = draft();
        project.publish();
        UUID versionOne = UUID.randomUUID();
        project.assignCurrentRuleVersion(versionOne);
        when(projects.findById(project.id())).thenReturn(Optional.of(project));
        when(ruleVersions.nextVersionNumber(project.id().value())).thenReturn(2);

        service.update(project.id().value(), update("Updated rules"));

        var snapshot = ArgumentCaptor.forClass(ProjectRuleVersionSnapshot.class);
        verify(ruleVersions).save(snapshot.capture());
        assertThat(snapshot.getValue().versionNumber()).isEqualTo(2);
        assertThat(snapshot.getValue().scoreConfig().rulesText()).isEqualTo("Updated rules");
        assertThat(snapshot.getValue().id()).isNotEqualTo(versionOne);
        assertThat(project.currentRuleVersionId()).isEqualTo(snapshot.getValue().id());
    }

    @Test
    void republishWithoutRuleEditReusesCurrentVersion() {
        ChallengeProject project = draft();
        project.publish();
        project.assignCurrentRuleVersion(UUID.randomUUID());
        project.archive();
        UUID currentVersion = project.currentRuleVersionId();
        when(projects.findById(project.id())).thenReturn(Optional.of(project));

        service.publish(project.id().value(), "Return to public library");

        verify(ruleVersions, never()).save(any());
        assertThat(project.status()).isEqualTo(ProjectStatus.PUBLISHED);
        assertThat(project.currentRuleVersionId()).isEqualTo(currentVersion);
    }

    @Test
    void archiveRequiresAndPersistsNormalizedReason() {
        ChallengeProject project = draft();
        project.publish();
        when(projects.findById(project.id())).thenReturn(Optional.of(project));

        service.archive(project.id().value(), "  Scheduled maintenance  ");

        var auditRecord = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(audit).record(auditRecord.capture());
        assertThat(auditRecord.getValue().action()).isEqualTo("PROJECT_ARCHIVE");
        assertThat(auditRecord.getValue().detail()).contains("\"reason\":\"Scheduled maintenance\"");
        assertThat(project.status()).isEqualTo(ProjectStatus.ARCHIVED);
    }

    @Test
    void republishRejectsBlankReason() {
        assertThatThrownBy(() -> service.publish(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 500");
    }

    private ChallengeProject draft() {
        return ChallengeProject.create(new ChallengeProjectId(UUID.randomUUID()), new ProjectName("Project"),
                new ProjectCategory("ATHLETICS"), new ScoreConfig(ScoreStorageType.INTEGER,
                        ScoreIndicatorType.NUMERIC, ComparisonDirection.HIGHER_BETTER,
                        "points", null, "BEST", null, "Initial rules", false),
                "Description", "Gym", "Timer");
    }

    private UpdateChallengeProjectCommand update(String rulesText) {
        return new UpdateChallengeProjectCommand("Project", "ATHLETICS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", false, "points", null, null,
                rulesText, "Description", "Gym", "Timer");
    }
}
