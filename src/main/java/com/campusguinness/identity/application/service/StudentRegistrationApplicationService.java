package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.PasswordPolicy;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.StudentRegistrationResult;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import com.campusguinness.school.application.query.port.StudentRegistrationSchoolQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StudentRegistrationApplicationService {

    private static final int MAX_PROOF_FILE_KEYS = 10;

    private final UserRepository users;
    private final UserAccountProvisioningPort provisioning;
    private final StudentIdentityApplicationRepository applications;
    private final StudentRegistrationSchoolQueryPort schools;
    private final PasswordHasher passwordHasher;

    public StudentRegistrationApplicationService(
            UserRepository users,
            UserAccountProvisioningPort provisioning,
            StudentIdentityApplicationRepository applications,
            StudentRegistrationSchoolQueryPort schools,
            PasswordHasher passwordHasher
    ) {
        this.users = users;
        this.provisioning = provisioning;
        this.applications = applications;
        this.schools = schools;
        this.passwordHasher = passwordHasher;
    }

    public StudentRegistrationResult register(RegisterStudentCommand command) {
        if (command == null) throw new IllegalArgumentException("command required");

        String username = normalize(command.username(), "username", 100);
        String realName = normalize(command.realName(), "realName", 100);
        String studentNumber = normalize(command.studentNumber(), "studentNumber", 64);
        String grade = normalize(command.grade(), "grade", 32);
        String className = normalize(command.className(), "className", 64);
        validateProofFileKeys(command.proofFileKeys());
        validatePasswords(command.password(), command.confirmPassword());
        validateSchool(command.schoolId());

        if (users.existsByUsername(username)) {
            throw error("USERNAME_ALREADY_EXISTS", "Username already exists.");
        }

        var user = User.create(new User.Builder()
                .id(new UserId(UUID.randomUUID()))
                .username(username));
        var savedUser = provisioning.create(user, passwordHasher.hash(command.password()));

        var application = StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(UUID.randomUUID()))
                .userId(savedUser.id().value())
                .schoolId(command.schoolId())
                .realName(realName)
                .studentNumber(studentNumber)
                .grade(grade)
                .className(className));
        applications.save(application);

        return new StudentRegistrationResult(
                savedUser.id().value(),
                application.id().value(),
                savedUser.username(),
                application.schoolId(),
                savedUser.status(),
                application.status(),
                Instant.now()
        );
    }

    private String normalize(String value, String field, int maxLength) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw error("INVALID_STUDENT_REGISTRATION_DATA", field + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw error("INVALID_STUDENT_REGISTRATION_DATA", field + " is too long.");
        }
        return normalized;
    }

    private void validateProofFileKeys(List<String> proofFileKeys) {
        List<String> keys = proofFileKeys == null ? List.of() : proofFileKeys;
        if (keys.size() > MAX_PROOF_FILE_KEYS) {
            throw error("INVALID_STUDENT_REGISTRATION_DATA", "Too many proof file keys.");
        }
        for (String key : keys) {
            if (key == null || key.isBlank() || key.length() > 500) {
                throw error("INVALID_STUDENT_REGISTRATION_DATA", "Invalid proof file key.");
            }
        }
        if (!keys.isEmpty()) {
            throw error("PROOF_ATTACHMENT_NOT_SUPPORTED", "Proof attachments are not supported yet.");
        }
    }

    private void validatePasswords(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw error("PASSWORD_CONFIRMATION_MISMATCH", "Password confirmation does not match.");
        }
        try {
            PasswordPolicy.validate(password);
        } catch (InvalidPasswordException ex) {
            throw error("PASSWORD_POLICY_VIOLATION", "Password does not satisfy policy.");
        }
    }

    private void validateSchool(UUID schoolId) {
        if (schoolId == null) {
            throw error("INVALID_STUDENT_REGISTRATION_DATA", "schoolId is required.");
        }
        var school = schools.findForStudentRegistration(schoolId);
        if (!school.exists()) {
            throw error("SCHOOL_NOT_FOUND", "School not found.");
        }
        if (!school.openForStudentRegistration()) {
            throw error("SCHOOL_NOT_OPEN_FOR_REGISTRATION", "School is not open for student registration.");
        }
    }

    private IdentityApplicationException error(String code, String message) {
        return new IdentityApplicationException(code, message);
    }
}
