package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityParticipantResult;
import com.campusguinness.activity.internal.domain.ActivityParticipant;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScope;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
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
class ActivityParticipantServiceTest {
    @Mock ActivityParticipantPort participants;
    @Mock SchoolResourceAuthorization schoolAuthorization;
    @Mock StudentSchoolScopeAuthorization studentAuthorization;

    private ActivityParticipantService service;
    private UUID schoolId;
    private UUID activityId;
    private UUID studentId;
    private UUID studentMembershipId;

    @BeforeEach
    void setUp() {
        service = new ActivityParticipantService(participants, schoolAuthorization, studentAuthorization);
        schoolId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        studentMembershipId = UUID.randomUUID();
    }

    @Test
    void listsParticipantsOnlyAfterResolvingTheAdminSchoolAndActivityScope() {
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(participants.findActivitySchool(activityId)).thenReturn(Optional.of(schoolId));
        var expected = new ActivityParticipantResult(
                studentId, "student", "S-001", "2026", "1班", null);
        when(participants.findParticipants(activityId, schoolId)).thenReturn(List.of(expected));

        assertThat(service.list(activityId)).containsExactly(expected);
        verify(participants).findParticipants(activityId, schoolId);
    }

    @Test
    void deniesAnActivityOutsideTheAdminSchool() {
        UUID otherSchoolId = UUID.randomUUID();
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(participants.findActivitySchool(activityId)).thenReturn(Optional.of(otherSchoolId));

        assertThatThrownBy(() -> service.list(activityId))
                .isInstanceOf(IdentityApplicationException.class)
                .satisfies(error -> assertThat(((IdentityApplicationException) error).code())
                        .isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED"));
        verify(participants, never()).findParticipants(any(), any());
    }

    @Test
    void assignsAnActiveSameSchoolStudentAndDoesNotAcceptClientScope() {
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(participants.findActivitySchool(activityId)).thenReturn(Optional.of(schoolId));
        when(participants.findActiveStudentMembership(studentId, schoolId))
                .thenReturn(Optional.of(studentMembershipId));
        when(participants.exists(activityId, studentMembershipId)).thenReturn(false);

        service.assign(activityId, studentId);

        ArgumentCaptor<ActivityParticipant> captor = ArgumentCaptor.forClass(ActivityParticipant.class);
        verify(participants).save(captor.capture());
        assertThat(captor.getValue().activityId()).isEqualTo(activityId);
        assertThat(captor.getValue().studentMembershipId()).isEqualTo(studentMembershipId);
    }

    @Test
    void rejectsDuplicateAssignmentBeforeWriting() {
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(participants.findActivitySchool(activityId)).thenReturn(Optional.of(schoolId));
        when(participants.findActiveStudentMembership(studentId, schoolId))
                .thenReturn(Optional.of(studentMembershipId));
        when(participants.exists(activityId, studentMembershipId)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(activityId, studentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Participant already exists");
        verify(participants, never()).save(any());
    }

    @Test
    void studentListUsesOnlyTheCurrentStudentScope() {
        var scope = new StudentSchoolScope(studentId, schoolId);
        var expected = new QueryPage<ActivityListResult>(List.of(), 0, 20, 0);
        when(studentAuthorization.requireUniqueActiveStudent()).thenReturn(scope);
        when(participants.findAssignedActivities(studentId, schoolId, 0, 20)).thenReturn(expected);

        assertThat(service.listAssigned(0, 20)).isSameAs(expected);
        verify(participants).findAssignedActivities(studentId, schoolId, 0, 20);
    }

    @Test
    void unassignedStudentDetailIsConcealedAsNotFound() {
        when(studentAuthorization.requireUniqueActiveStudent())
                .thenReturn(new StudentSchoolScope(studentId, schoolId));
        when(participants.findAssignedActivity(studentId, schoolId, activityId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignedDetail(activityId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activity not found");
    }
}
