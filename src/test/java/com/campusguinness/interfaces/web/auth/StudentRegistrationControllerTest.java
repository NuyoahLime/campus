package com.campusguinness.interfaces.web.auth;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.result.StudentRegistrationResult;
import com.campusguinness.identity.application.service.StudentIdentityApplicationResubmissionService;
import com.campusguinness.identity.application.service.StudentRegistrationApplicationService;
import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import com.campusguinness.interfaces.web.common.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentRegistrationControllerTest {

    StudentRegistrationApplicationService service;
    StudentIdentityApplicationResubmissionService resubmissionService;
    MockMvc mvc;
    ObjectMapper mapper;
    UUID schoolId;

    @BeforeEach
    void setUp() {
        service = mock(StudentRegistrationApplicationService.class);
        resubmissionService = mock(StudentIdentityApplicationResubmissionService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders
                .standaloneSetup(new StudentRegistrationController(service, resubmissionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        schoolId = UUID.randomUUID();
    }

    @Test
    void validRequestReturns201NoStoreAndNoPasswordFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(service.register(any())).thenReturn(new StudentRegistrationResult(
                userId, applicationId, "student_001", schoolId,
                AccountStatus.PENDING_ACTIVATION, StudentIdentityApplicationStatus.PENDING,
                Instant.parse("2026-08-06T00:00:00Z")));

        mvc.perform(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.username").value("student_001"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.confirmPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.platformRole").doesNotExist());
    }

    @Test
    void validResubmissionRequestReturns201NoStoreAndNoPasswordFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(resubmissionService.resubmit(any())).thenReturn(new StudentRegistrationResult(
                userId, applicationId, "student_001", schoolId,
                AccountStatus.PENDING_ACTIVATION, StudentIdentityApplicationStatus.PENDING,
                Instant.parse("2026-08-06T00:00:00Z")));

        mvc.perform(post("/api/v1/auth/student/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validResubmissionBody())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.username").value("student_001"))
                .andExpect(jsonPath("$.schoolId").value(schoolId.toString()))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.userIdSubmittedByClient").doesNotExist());
    }

    @Test
    void missingUsernameReturns400() throws Exception {
        assertValidationFailed(bodyWithout("username"));
    }

    @Test
    void blankPasswordReturns400() throws Exception {
        var body = validBody();
        body.put("password", "");
        assertValidationFailed(body);
    }

    @Test
    void passwordConfirmationMismatchReturnsStableCode() throws Exception {
        when(service.register(any())).thenThrow(new IdentityApplicationException(
                "PASSWORD_CONFIRMATION_MISMATCH", "Password confirmation does not match."));
        var body = validBody();
        body.put("confirmPassword", "DifferentPassword123!");

        mvc.perform(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));
    }

    @Test
    void blankRealNameReturns400() throws Exception {
        var body = validBody();
        body.put("realName", " ");
        assertValidationFailed(body);
    }

    @Test
    void nullSchoolIdReturns400() throws Exception {
        var body = validBody();
        body.put("schoolId", null);
        assertValidationFailed(body);
    }

    @Test
    void blankStudentNumberReturns400() throws Exception {
        var body = validBody();
        body.put("studentNumber", " ");
        assertValidationFailed(body);
    }

    @Test
    void blankGradeReturns400() throws Exception {
        var body = validBody();
        body.put("grade", " ");
        assertValidationFailed(body);
    }

    @Test
    void blankClassNameReturns400() throws Exception {
        var body = validBody();
        body.put("className", " ");
        assertValidationFailed(body);
    }

    @Test
    void overlongFieldReturns400() throws Exception {
        var body = validBody();
        body.put("grade", "x".repeat(33));
        assertValidationFailed(body);
    }

    private void assertValidationFailed(Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/v1/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private Map<String, Object> bodyWithout(String key) {
        var body = validBody();
        body.remove(key);
        return body;
    }

    private Map<String, Object> validBody() {
        return new java.util.LinkedHashMap<>(Map.of(
                "username", "student_001",
                "password", "SecurePassword123!",
                "confirmPassword", "SecurePassword123!",
                "realName", "Zhang San",
                "schoolId", schoolId.toString(),
                "studentNumber", "20260001",
                "grade", "Grade 10",
                "className", "Class 1",
                "proofFileKeys", List.of()
        ));
    }

    private Map<String, Object> validResubmissionBody() {
        return new java.util.LinkedHashMap<>(Map.of(
                "username", "student_001",
                "password", "SecurePassword123!",
                "realName", "Zhang San",
                "studentNumber", "20260002",
                "grade", "Grade 11",
                "className", "Class 2",
                "proofFileKeys", List.of()
        ));
    }
}
