package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.activity.internal.persistence.TeacherApplicationQueryAdapter;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.activity.application.query.port.TeacherApplicationQueryPort;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherApplicationQueryAdapterTest {

    @Mock JdbcTemplate jdbc;
    @Mock ActivityApplicationRepository appRepo;
    @Mock ActivityRepository activityRepo;
    @Mock SchoolMembershipQueryPort membershipPort;

    UUID userId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();

    @Test void findMineByIdReturnsSchoolName() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", appId); row.put("school_id", schoolId); row.put("school_name", "Test School");
        row.put("title", "t"); row.put("description", "d"); row.put("application_status", "SUBMITTED");
        row.put("application_version", 1); row.put("created_activity_id", null);
        row.put("reviewed_at", null); row.put("review_comment", null); row.put("reject_reason", null);
        row.put("created_at", new java.sql.Timestamp(System.currentTimeMillis()));
        row.put("updated_at", new java.sql.Timestamp(System.currentTimeMillis()));
        when(jdbc.queryForList(anyString(), eq(appId), eq(userId))).thenReturn(List.of(row));

        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var result = port.findMineById(userId, appId);
        assertThat(result).isPresent();
        assertThat(result.get().schoolName()).isEqualTo("Test School");
        assertThat(result.get().createdAt()).isNotNull();
    }

    @Test void findMineByIdReturnsEmptyForOtherUser() {
        when(jdbc.queryForList(anyString(), eq(appId), eq(userId))).thenReturn(List.of());
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        assertThat(port.findMineById(userId, appId)).isEmpty();
    }

    @Test void getStatsReturnsCorrectCounts() {
        Map<String, Object> row = new HashMap<>();
        row.put("total", 10L); row.put("draft", 2L); row.put("submitted", 3L);
        row.put("approved", 4L); row.put("rejected", 1L); row.put("withdrawn", 0L);
        when(jdbc.queryForMap(anyString(), eq(userId))).thenReturn(row);
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var stats = port.getStats(userId);
        assertThat(stats.total()).isEqualTo(10);
        assertThat(stats.submitted()).isEqualTo(3);
    }

    @Test void findTeacherSchoolsReturnsTeacherOnly() {
        Map<String, Object> row = new HashMap<>();
        row.put("school_id", schoolId); row.put("school_name", "My School");
        when(jdbc.queryForList(anyString(), eq(userId))).thenReturn(List.of(row));
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var schools = port.findTeacherSchools(userId);
        assertThat(schools).hasSize(1);
        assertThat(schools.getFirst().schoolName()).isEqualTo("My School");
    }

    @Test void updateDraftChecksOwnerFirst() {
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var svc = new ActivityApplicationService(appRepo, activityRepo, membershipPort, port);
        when(appRepo.findByIdAndApplicantId(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.updateDraft(appId, userId, "title", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test void updateDraftRejectsBlankTitle() {
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var svc = new ActivityApplicationService(appRepo, activityRepo, membershipPort, port);
        var app = ActivityApplication.reconstitute(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(appId)).schoolId(schoolId).applicantId(userId)
                .title("old").description("old").status(ApplicationStatus.DRAFT));
        when(appRepo.findByIdAndApplicantId(appId, userId)).thenReturn(Optional.of(app));
        when(membershipPort.hasActiveTeacherMembership(userId, schoolId)).thenReturn(true);

        assertThatThrownBy(() -> svc.updateDraft(appId, userId, "   ", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test void resubmitChecksMembership() {
        TeacherApplicationQueryPort port = new TeacherApplicationQueryAdapter(jdbc);
        var svc = new ActivityApplicationService(appRepo, activityRepo, membershipPort, port);
        var app = ActivityApplication.reconstitute(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(appId)).schoolId(schoolId).applicantId(userId)
                .title("t").description("d").status(ApplicationStatus.DRAFT));
        when(appRepo.findByIdAndApplicantId(appId, userId)).thenReturn(Optional.of(app));
        when(membershipPort.hasActiveTeacherMembership(userId, schoolId)).thenReturn(false);

        assertThatThrownBy(() -> svc.resubmit(appId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active TEACHER membership");
    }
}
