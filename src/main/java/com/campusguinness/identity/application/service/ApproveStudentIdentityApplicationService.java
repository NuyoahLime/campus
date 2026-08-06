package com.campusguinness.identity.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.CreateStudentProfileCommand;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.port.StudentProfileCommandPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ApproveStudentIdentityApplicationService {

    private final StudentIdentityReviewAuthorization authorization;
    private final StudentIdentityApplicationRepository applications;
    private final UserRepository users;
    private final StudentProfileCommandPort profiles;
    private final AuditRecordCommandPort audit;

    public ApproveStudentIdentityApplicationService(
            StudentIdentityReviewAuthorization authorization,
            StudentIdentityApplicationRepository applications,
            UserRepository users,
            StudentProfileCommandPort profiles,
            AuditRecordCommandPort audit
    ) {
        this.authorization = authorization;
        this.applications = applications;
        this.users = users;
        this.profiles = profiles;
        this.audit = audit;
    }

    public StudentIdentityApplicationReviewResult approve(UUID schoolId, UUID applicationId) {
        UUID actorId = authorization.requireSchoolAdmin(schoolId);
        if (applicationId == null) throw new IllegalArgumentException("applicationId required");
        Instant now = Instant.now();

        try {
            var application = findApplicationForUpdate(applicationId);
            ensureApplicationBelongsToSchool(application, schoolId);
            ensurePending(application);

            var user = users.findByIdForUpdate(new UserId(application.userId()))
                    .orElseThrow(() -> error("APPLICANT_ACCOUNT_NOT_ACTIVATABLE", "Applicant account cannot be activated."));
            ensureUserActivatable(user, schoolId);
            ensureNoProfile(user.id().value());

            user.activate();
            var membership = user.grantStudentMembership(new SchoolMembershipId(UUID.randomUUID()), schoolId, now);
            application.approve(actorId, now);

            users.save(user);
            profiles.create(new CreateStudentProfileCommand(
                    UUID.randomUUID(),
                    membership.id().value(),
                    application.grade(),
                    application.className(),
                    application.studentNumber()
            ));
            applications.save(application);
            audit.record(auditCommand(
                    "STUDENT_APPLICATION_APPROVED",
                    actorId,
                    schoolId,
                    application.id().value(),
                    application.userId(),
                    "SUCCESS",
                    now
            ));

            return new StudentIdentityApplicationReviewResult(
                    application.id().value(),
                    application.userId(),
                    application.schoolId(),
                    application.status().name(),
                    user.status().name(),
                    membership.roleInSchool(),
                    membership.status().name(),
                    null,
                    now
            );
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw error("STUDENT_APPROVAL_CONFLICT", "Student application approval conflict.");
        }
    }

    private StudentIdentityApplication findApplicationForUpdate(UUID applicationId) {
        return applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))
                .orElseThrow(() -> error("STUDENT_APPLICATION_NOT_FOUND", "Student application not found."));
    }

    private void ensureApplicationBelongsToSchool(StudentIdentityApplication application, UUID schoolId) {
        if (!application.schoolId().equals(schoolId)) {
            throw error("STUDENT_APPLICATION_NOT_FOUND", "Student application not found.");
        }
    }

    private void ensurePending(StudentIdentityApplication application) {
        if (application.status() != StudentIdentityApplicationStatus.PENDING) {
            throw error("STUDENT_APPLICATION_NOT_PENDING", "Student application is not pending.");
        }
    }

    private void ensureUserActivatable(User user, UUID schoolId) {
        if (user.status() != AccountStatus.PENDING_ACTIVATION) {
            throw error("APPLICANT_ACCOUNT_NOT_ACTIVATABLE", "Applicant account cannot be activated.");
        }
        if (user.activeMembershipFor(schoolId).isPresent()) {
            throw error("STUDENT_MEMBERSHIP_CONFLICT", "Student membership already exists.");
        }
    }

    private void ensureNoProfile(UUID userId) {
        if (profiles.existsByUserId(userId)) {
            throw error("STUDENT_PROFILE_ALREADY_EXISTS", "Student profile already exists.");
        }
    }

    private AuditRecordCommand auditCommand(
            String action,
            UUID actorId,
            UUID schoolId,
            UUID applicationId,
            UUID targetUserId,
            String result,
            Instant occurredAt
    ) {
        String detail = """
                {"applicationId":"%s","targetUserId":"%s","result":"%s"}
                """.formatted(applicationId, targetUserId, result).trim();
        return new AuditRecordCommand(
                UUID.randomUUID(),
                schoolId,
                actorId,
                action,
                "STUDENT_IDENTITY_APPLICATION",
                applicationId,
                detail,
                occurredAt
        );
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }
}
