package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.query.model.AchievementStatus;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAchievementApplicationServiceTest {

    @Mock AchievementRecordQueryPort records;

    private StudentAchievementApplicationService service;
    private UUID studentId;
    private UUID recordId;

    @BeforeEach
    void setUp() {
        service = new StudentAchievementApplicationService(records);
        studentId = UUID.randomUUID();
        recordId = UUID.randomUUID();
    }

    @Test
    void studentListsOwnRecordsOnly() {
        QueryPage<AchievementRecordItem> page =
                new QueryPage<>(List.of(item(AchievementStatus.ACTIVE)), 0, 20, 1);
        when(records.findStudentRecords(studentId, null, null, 0, 20))
                .thenReturn(page);

        assertThat(service.list(studentId, null, null, 0, 20))
                .isSameAs(page);
        verify(records).findStudentRecords(studentId, null, null, 0, 20);
    }

    @Test
    void studentCannotReadOtherStudentRecord() {
        when(records.findStudentRecord(studentId, recordId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(studentId, recordId))
                .isInstanceOf(AchievementNotFoundException.class);
    }

    @Test
    void inactiveMembershipDoesNotHideHistoricalRecord() {
        AchievementRecordDetail detail = detail(AchievementStatus.ACTIVE);
        when(records.findStudentRecord(studentId, recordId))
                .thenReturn(Optional.of(detail));

        assertThat(service.get(studentId, recordId)).isSameAs(detail);
    }

    @Test
    void activeAndRevokedRecordsAreReturned() {
        QueryPage<AchievementRecordItem> page = new QueryPage<>(
                List.of(
                        item(AchievementStatus.ACTIVE),
                        item(AchievementStatus.REVOKED)),
                0,
                20,
                2);
        when(records.findStudentRecords(studentId, null, null, 0, 20))
                .thenReturn(page);

        assertThat(service.list(studentId, null, null, 0, 20).items())
                .extracting(AchievementRecordItem::status)
                .containsExactly(
                        AchievementStatus.ACTIVE,
                        AchievementStatus.REVOKED);
    }

    @Test
    void statusFilterWorks() {
        when(records.findStudentRecords(
                        studentId, "REVOKED", null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        service.list(studentId, "REVOKED", null, 0, 20);

        verify(records).findStudentRecords(
                studentId, "REVOKED", null, 0, 20);
    }

    @Test
    void keywordIsTrimmed() {
        when(records.findStudentRecords(
                        studentId, null, "Activity", 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        service.list(studentId, null, " Activity ", 0, 20);

        verify(records).findStudentRecords(
                studentId, null, "Activity", 0, 20);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        when(records.findStudentRecords(studentId, null, null, 1, 5))
                .thenReturn(new QueryPage<>(List.of(), 1, 5, 11));

        assertThat(service.list(studentId, null, null, 1, 5).totalElements())
                .isEqualTo(11);
    }

    @Test
    void invalidStatusIsRejected() {
        assertThatThrownBy(() ->
                service.list(studentId, "PENDING", null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oversizedKeywordIsRejected() {
        assertThatThrownBy(() ->
                service.list(studentId, null, "x".repeat(101), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidPageIsRejected() {
        assertThatThrownBy(() ->
                service.list(studentId, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidSizeIsRejected() {
        assertThatThrownBy(() ->
                service.list(studentId, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detailReturnsRevocationReason() {
        AchievementRecordDetail detail = detail(AchievementStatus.REVOKED);
        when(records.findStudentRecord(studentId, recordId))
                .thenReturn(Optional.of(detail));

        assertThat(service.get(studentId, recordId).revocationReason())
                .isEqualTo("ranking withdrawn");
    }

    private AchievementRecordItem item(AchievementStatus status) {
        return new AchievementRecordItem(
                recordId,
                "Activity · Project · 第1名",
                "School",
                "Activity",
                "Project",
                1,
                1,
                "100",
                "INTEGER",
                "a".repeat(32),
                status,
                Instant.parse("2026-07-31T00:00:00Z"),
                status == AchievementStatus.REVOKED
                        ? Instant.parse("2026-07-31T01:00:00Z")
                        : null);
    }

    private AchievementRecordDetail detail(AchievementStatus status) {
        AchievementRecordItem item = item(status);
        return new AchievementRecordDetail(
                item.recordId(),
                item.recordTitle(),
                item.schoolName(),
                item.activityTitle(),
                item.projectName(),
                item.rankingVersionNumber(),
                item.rankPosition(),
                item.scoreDisplayValue(),
                item.scoreStorageType(),
                item.verificationCode(),
                item.status(),
                item.issuedAt(),
                item.revokedAt(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status == AchievementStatus.REVOKED
                        ? "ranking withdrawn"
                        : null);
    }
}
