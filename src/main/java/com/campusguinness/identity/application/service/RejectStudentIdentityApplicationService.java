package com.campusguinness.identity.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class RejectStudentIdentityApplicationService {

    private static final int MAX_REASON_LENGTH = 2000;

    private final StudentIdentityReviewAuthorization authorization;
    private final StudentIdentityApplicationRepository applications;
    private final AuditRecordCommandPort audit;

    public RejectStudentIdentityApplicationService(
            StudentIdentityReviewAuthorization authorization,
            StudentIdentityApplicationRepository applications,
            AuditRecordCommandPort audit
    ) {
        this.authorization = authorization;
        this.applications = applications;
        this.audit = audit;
    }

    public StudentIdentityApplicationReviewResult reject(UUID schoolId, UUID applicationId, String reason) {
        UUID actorId = authorization.requireSchoolAdmin(schoolId);
        if (applicationId == null) throw new IllegalArgumentException("applicationId required");
        String normalizedReason = normalizeReason(reason);
        Instant now = Instant.now();

        try {
            var application = applications.findByIdForUpdate(new StudentIdentityApplicationId(applicationId))
                    .orElseThrow(() -> error("STUDENT_APPLICATION_NOT_FOUND", "Student application not found."));
            if (!application.schoolId().equals(schoolId)) {
                throw error("STUDENT_APPLICATION_NOT_FOUND", "Student application not found.");
            }
            if (application.status() != StudentIdentityApplicationStatus.PENDING) {
                throw error("STUDENT_APPLICATION_NOT_PENDING", "Student application is not pending.");
            }

            application.reject(actorId, now, normalizedReason);
            applications.save(application);
            audit.record(auditCommand(
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
                    "PENDING_ACTIVATION",
                    null,
                    null,
                    normalizedReason,
                    now
            );
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw error("STUDENT_APPLICATION_NOT_PENDING", "Student application is not pending.");
        }
    }

    private String normalizeReason(String reason) {
        String normalized = reason != null ? reason.trim() : "";
        if (normalized.isBlank()) {
            throw error("REJECTION_REASON_REQUIRED", "Rejection reason is required.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw error("REJECTION_REASON_TOO_LONG", "Rejection reason is too long.");
        }
        return normalized;
    }

    private AuditRecordCommand auditCommand(
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
                "STUDENT_APPLICATION_REJECTED",
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
