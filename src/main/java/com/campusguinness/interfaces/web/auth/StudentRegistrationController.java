package com.campusguinness.interfaces.web.auth;

import com.campusguinness.identity.application.service.RegisterStudentCommand;
import com.campusguinness.identity.application.service.ResubmitStudentIdentityApplicationCommand;
import com.campusguinness.identity.application.service.StudentIdentityApplicationResubmissionService;
import com.campusguinness.identity.application.service.StudentRegistrationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/student")
public class StudentRegistrationController {

    private final StudentRegistrationApplicationService service;
    private final StudentIdentityApplicationResubmissionService resubmissionService;

    public StudentRegistrationController(
            StudentRegistrationApplicationService service,
            StudentIdentityApplicationResubmissionService resubmissionService
    ) {
        this.service = service;
        this.resubmissionService = resubmissionService;
    }

    @PostMapping("/register")
    public ResponseEntity<StudentRegistrationResponse> register(@Valid @RequestBody StudentRegistrationRequest request) {
        var result = service.register(new RegisterStudentCommand(
                request.username(),
                request.password(),
                request.confirmPassword(),
                request.realName(),
                request.schoolId(),
                request.studentNumber(),
                request.grade(),
                request.className(),
                request.proofFileKeys()
        ));
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(new StudentRegistrationResponse(
                        result.userId(),
                        result.applicationId(),
                        result.username(),
                        result.schoolId(),
                        result.accountStatus().name(),
                        result.applicationStatus().name(),
                        result.submittedAt()
                ));
    }

    @PostMapping("/resubmit")
    public ResponseEntity<StudentRegistrationResponse> resubmit(
            @Valid @RequestBody StudentIdentityResubmissionRequest request) {
        var result = resubmissionService.resubmit(new ResubmitStudentIdentityApplicationCommand(
                request.username(),
                request.password(),
                request.realName(),
                request.studentNumber(),
                request.grade(),
                request.className(),
                request.proofFileKeys()
        ));
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(new StudentRegistrationResponse(
                        result.userId(),
                        result.applicationId(),
                        result.username(),
                        result.schoolId(),
                        result.accountStatus().name(),
                        result.applicationStatus().name(),
                        result.submittedAt()
                ));
    }
}
