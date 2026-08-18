package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.StudentScoreDetailResult;
import com.campusguinness.score.application.query.model.StudentScoreListResult;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StudentScoreQueryService {
    private final StudentScoreQueryPort queryPort;
    private final CurrentActor currentActor;
    private final AuthenticationMembershipQuery memberships;

    public StudentScoreQueryService(StudentScoreQueryPort queryPort,
                                    CurrentActor currentActor,
                                    AuthenticationMembershipQuery memberships) {
        this.queryPort = queryPort;
        this.currentActor = currentActor;
        this.memberships = memberships;
    }

    public QueryPage<StudentScoreListResult> list(int page, int size) {
        validatePage(page, size);
        StudentIdentity identity = requireStudentIdentity();
        return queryPort.findVisibleByStudent(identity.studentId(), identity.schoolId(), page, size);
    }

    public StudentScoreDetailResult detail(UUID scoreAttemptId) {
        if (scoreAttemptId == null) throw new IllegalArgumentException("scoreAttemptId required");
        StudentIdentity identity = requireStudentIdentity();
        return queryPort.findVisibleById(scoreAttemptId, identity.studentId(), identity.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("Score attempt not found: " + scoreAttemptId));
    }

    private StudentIdentity requireStudentIdentity() {
        UUID actorId = currentActor.requireUserId();
        List<AuthenticationMembership> active = memberships.findActiveByUserId(actorId);
        if (active.size() != 1 || !"STUDENT".equals(active.getFirst().roleInSchool())) {
            throw new IdentityApplicationException(
                    "STUDENT_SCOPE_DENIED",
                    "A unique active STUDENT membership is required.");
        }
        return new StudentIdentity(actorId, active.getFirst().schoolId());
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }

    private record StudentIdentity(UUID studentId, UUID schoolId) {}
}
