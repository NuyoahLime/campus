package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.exception.AchievementVerificationCodeCollisionException;
import com.campusguinness.achievement.application.exception.AchievementVerificationCodeGenerationException;
import com.campusguinness.achievement.application.port.AchievementIssuancePort;
import com.campusguinness.achievement.application.query.model.AchievementIssueResult;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementIssuanceApplicationServiceTest {

    @Mock SchoolMembershipQueryPort memberships;
    @Mock AchievementIssuancePort issuance;
    @Mock AchievementRecordQueryPort records;

    private UUID actorId;
    private UUID schoolId;
    private UUID entryId;
    private SchoolAdminAchievementApplicationService service;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        service = new SchoolAdminAchievementApplicationService(
                memberships, issuance, records, () -> "a".repeat(32));
    }

    @Test
    void schoolAdminCanIssueOwnSchoolCurrentEntry() {
        allowAdmin();
        AchievementIssueResult expected =
                new AchievementIssueResult(detail(actorId, true), true);
        when(issuance.issueForSchool(
                        schoolId, entryId, actorId, "a".repeat(32)))
                .thenReturn(Optional.of(expected));

        assertThat(service.issue(actorId, entryId)).isSameAs(expected);
    }

    @Test
    void otherSchoolEntryLooksNotFound() {
        allowAdmin();
        when(issuance.issueForSchool(
                        schoolId, entryId, actorId, "a".repeat(32)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(actorId, entryId))
                .isInstanceOf(AchievementNotFoundException.class)
                .hasMessage("Achievement record not found");
    }

    @Test
    void inactiveSchoolAdminCannotIssue() {
        when(memberships.findActiveSchoolAdminSchoolId(actorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(actorId, entryId))
                .isInstanceOf(AccessDeniedException.class);
        verify(issuance, never()).issueForSchool(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyString());
    }

    @Test
    void verificationCodeIsLowercaseHex32() {
        SchoolAdminAchievementApplicationService productionService =
                new SchoolAdminAchievementApplicationService(
                        memberships, issuance, records);
        allowAdmin();
        when(issuance.issueForSchool(
                        org.mockito.ArgumentMatchers.eq(schoolId),
                        org.mockito.ArgumentMatchers.eq(entryId),
                        org.mockito.ArgumentMatchers.eq(actorId),
                        anyString()))
                .thenReturn(Optional.of(
                        new AchievementIssueResult(detail(actorId, true), true)));

        productionService.issue(actorId, entryId);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(issuance).issueForSchool(
                org.mockito.ArgumentMatchers.eq(schoolId),
                org.mockito.ArgumentMatchers.eq(entryId),
                org.mockito.ArgumentMatchers.eq(actorId),
                code.capture());
        assertThat(code.getValue()).matches("[0-9a-f]{32}");
    }

    @Test
    void firstIssueCreatesRecord() {
        allowAdmin();
        when(issuance.issueForSchool(
                        schoolId, entryId, actorId, "a".repeat(32)))
                .thenReturn(Optional.of(
                        new AchievementIssueResult(detail(actorId, true), true)));

        assertThat(service.issue(actorId, entryId).created()).isTrue();
    }

    @Test
    void duplicateIssueReturnsExistingRecord() {
        allowAdmin();
        SchoolAdminAchievementDetail existing = detail(actorId, false);
        when(issuance.issueForSchool(
                        schoolId, entryId, actorId, "a".repeat(32)))
                .thenReturn(Optional.of(
                        new AchievementIssueResult(existing, false)));

        AchievementIssueResult result = service.issue(actorId, entryId);

        assertThat(result.created()).isFalse();
        assertThat(result.record().recordId()).isEqualTo(existing.recordId());
    }

    @Test
    void duplicateIssueKeepsOriginalIssuer() {
        allowAdmin();
        UUID originalIssuer = UUID.randomUUID();
        SchoolAdminAchievementDetail existing = detail(originalIssuer, false);
        when(issuance.issueForSchool(
                        schoolId, entryId, actorId, "a".repeat(32)))
                .thenReturn(Optional.of(
                        new AchievementIssueResult(existing, false)));

        assertThat(service.issue(actorId, entryId).record().issuedBy())
                .isEqualTo(originalIssuer);
    }

    @Test
    void collisionRetriesAtMostThreeTimes() {
        allowAdmin();
        AtomicInteger generated = new AtomicInteger();
        service = new SchoolAdminAchievementApplicationService(
                memberships,
                issuance,
                records,
                () -> "%032x".formatted(generated.incrementAndGet()));
        when(issuance.issueForSchool(
                        org.mockito.ArgumentMatchers.eq(schoolId),
                        org.mockito.ArgumentMatchers.eq(entryId),
                        org.mockito.ArgumentMatchers.eq(actorId),
                        anyString()))
                .thenThrow(new AchievementVerificationCodeCollisionException());

        assertThatThrownBy(() -> service.issue(actorId, entryId))
                .isInstanceOf(AchievementVerificationCodeGenerationException.class);
        assertThat(generated).hasValue(3);
    }

    private void allowAdmin() {
        when(memberships.findActiveSchoolAdminSchoolId(actorId))
                .thenReturn(Optional.of(schoolId));
    }

    private SchoolAdminAchievementDetail detail(
            UUID issuedBy, boolean created) {
        return new SchoolAdminAchievementDetail(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                entryId,
                UUID.randomUUID(),
                "Student",
                "School",
                "Activity",
                "Project",
                1,
                "100",
                "INTEGER",
                "Activity · Project · 第1名",
                "a".repeat(32),
                AchievementStatus.ACTIVE,
                Instant.parse("2026-07-31T00:00:00Z"),
                issuedBy,
                "Issuer",
                null,
                null,
                null,
                created);
    }
}
