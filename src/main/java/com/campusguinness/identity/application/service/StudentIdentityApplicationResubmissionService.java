package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.port.LoginCredentialCommandPort;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.result.StudentRegistrationResult;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StudentIdentityApplicationResubmissionService {

    private static final int MAX_PROOF_FILE_KEYS = 10;

    private final AuthenticationAccountQuery accounts;
    private final LoginCredentialCommandPort credentials;
    private final PasswordHasher passwordHasher;
    private final UserRepository users;
    private final StudentIdentityApplicationRepository applications;
    private final Clock clock;
    private final String dummyPasswordHash;

    public StudentIdentityApplicationResubmissionService(
            AuthenticationAccountQuery accounts,
            LoginCredentialCommandPort credentials,
            PasswordHasher passwordHasher,
            UserRepository users,
            StudentIdentityApplicationRepository applications,
            Clock clock
    ) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.users = users;
        this.applications = applications;
        this.clock = clock;
        this.dummyPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    public StudentRegistrationResult resubmit(ResubmitStudentIdentityApplicationCommand command) {
        if (command == null) throw new IllegalArgumentException("command required");

        String username = normalize(command.username(), "username", 100);
        String realName = normalize(command.realName(), "realName", 100);
        String studentNumber = normalize(command.studentNumber(), "studentNumber", 64);
        String grade = normalize(command.grade(), "grade", 32);
        String className = normalize(command.className(), "className", 64);
        validateProofFileKeys(command.proofFileKeys());

        AuthenticationAccount account = accounts.findByLoginName(username).orElse(null);
        if (account == null) {
            passwordHasher.matches(command.password() != null ? command.password() : "", dummyPasswordHash);
            throw authenticationFailed();
        }
        if (!passwordHasher.matches(command.password() != null ? command.password() : "", account.passwordHash())) {
            credentials.recordPasswordFailure(account.userId());
            throw authenticationFailed();
        }
        credentials.resetPasswordFailures(account.userId());
        if ("LOCKED".equals(account.accountStatus())
                || (account.lockedUntil() != null && account.lockedUntil().isAfter(clock.instant()))) {
            throw error("ACCOUNT_LOCKED", "The account is locked.");
        }
        if ("DISABLED".equals(account.accountStatus())) {
            throw error("ACCOUNT_DISABLED", "The account is disabled.");
        }

        var user = users.findByIdForUpdate(new UserId(account.userId()))
                .orElseThrow(() -> error("AUTHENTICATION_FAILED", "The username or password is invalid."));
        if (user.status() != AccountStatus.PENDING_ACTIVATION) {
            throw notResubmittable();
        }

        var latest = applications.findLatestByUserIdForUpdate(user.id().value())
                .orElseThrow(this::notResubmittable);
        if (latest.status() != StudentIdentityApplicationStatus.REJECTED) {
            throw notResubmittable();
        }

        var newApplication = StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(UUID.randomUUID()))
                .userId(user.id().value())
                .schoolId(latest.schoolId())
                .realName(realName)
                .studentNumber(studentNumber)
                .grade(grade)
                .className(className));
        applications.save(newApplication);

        return new StudentRegistrationResult(
                user.id().value(),
                newApplication.id().value(),
                user.username(),
                newApplication.schoolId(),
                user.status(),
                newApplication.status(),
                Instant.now()
        );
    }

    private String normalize(String value, String field, int maxLength) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw error("INVALID_STUDENT_RESUBMISSION_DATA", field + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw error("INVALID_STUDENT_RESUBMISSION_DATA", field + " is too long.");
        }
        return normalized;
    }

    private void validateProofFileKeys(List<String> proofFileKeys) {
        List<String> keys = proofFileKeys == null ? List.of() : proofFileKeys;
        if (keys.size() > MAX_PROOF_FILE_KEYS) {
            throw error("INVALID_STUDENT_RESUBMISSION_DATA", "Too many proof file keys.");
        }
        for (String key : keys) {
            if (key == null || key.isBlank() || key.length() > 500) {
                throw error("INVALID_STUDENT_RESUBMISSION_DATA", "Invalid proof file key.");
            }
        }
        if (!keys.isEmpty()) {
            throw error("PROOF_ATTACHMENT_NOT_SUPPORTED", "Proof attachments are not supported yet.");
        }
    }

    private IdentityApplicationException authenticationFailed() {
        return error("AUTHENTICATION_FAILED", "The username or password is invalid.");
    }

    private IdentityApplicationException notResubmittable() {
        return error("STUDENT_APPLICATION_NOT_RESUBMITTABLE",
                "Student identity application is not eligible for resubmission.");
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }
}
