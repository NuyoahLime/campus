package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.exception.ActivityParticipantAlreadyAssignedException;
import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityParticipantResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.internal.domain.ActivityParticipant;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScope;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActivityParticipantService {
    private final ActivityParticipantPort participants;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final StudentSchoolScopeAuthorization studentAuthorization;

    public ActivityParticipantService(ActivityParticipantPort participants,
                                      SchoolResourceAuthorization schoolAuthorization,
                                      StudentSchoolScopeAuthorization studentAuthorization) {
        this.participants = participants;
        this.schoolAuthorization = schoolAuthorization;
        this.studentAuthorization = studentAuthorization;
    }

    @Transactional(readOnly = true)
    public List<ActivityParticipantResult> list(UUID activityId) {
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        requireActivityInSchool(activityId, schoolId);
        return participants.findParticipants(activityId, schoolId);
    }

    @Transactional(readOnly = true)
    public List<ActivityParticipantResult> candidates(UUID activityId, String query) {
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        requireActivityInSchool(activityId, schoolId);
        return participants.findCandidates(activityId, schoolId, normalize(query));
    }

    public void assign(UUID activityId, UUID studentId) {
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        requireActivityInSchool(activityId, schoolId);
        UUID membershipId = participants.findActiveStudentMembership(studentId, schoolId)
                .orElseThrow(() -> new IdentityApplicationException(
                        "STUDENT_SCOPE_DENIED",
                        "The target must have an active STUDENT membership in this school."));
        if (participants.exists(activityId, membershipId)) {
            throw new ActivityParticipantAlreadyAssignedException();
        }
        participants.save(ActivityParticipant.assign(activityId, membershipId, Instant.now()));
    }

    public void remove(UUID activityId, UUID studentId) {
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        UUID schoolId = schoolAuthorization.requireUniqueSchoolAdminSchool();
        requireActivityInSchool(activityId, schoolId);
        UUID membershipId = participants.findActiveStudentMembership(studentId, schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + studentId));
        if (!participants.delete(activityId, membershipId)) {
            throw new IllegalArgumentException("Participant not found: " + studentId);
        }
    }

    @Transactional(readOnly = true)
    public QueryPage<ActivityListResult> listAssigned(int page, int size) {
        validatePage(page, size);
        StudentSchoolScope scope = studentAuthorization.requireUniqueActiveStudent();
        return participants.findAssignedActivities(scope.studentId(), scope.schoolId(), page, size);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResult assignedDetail(UUID activityId) {
        if (activityId == null) throw new IllegalArgumentException("activityId required");
        StudentSchoolScope scope = studentAuthorization.requireUniqueActiveStudent();
        return participants.findAssignedActivity(scope.studentId(), scope.schoolId(), activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
    }

    private void requireActivityInSchool(UUID activityId, UUID schoolId) {
        if (activityId == null) throw new IllegalArgumentException("activityId required");
        UUID actualSchool = participants.findActivitySchool(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        if (!actualSchool.equals(schoolId)) {
            throw new IdentityApplicationException(
                    "SCHOOL_ADMIN_SCOPE_DENIED",
                    "Activity administration scope denied.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
