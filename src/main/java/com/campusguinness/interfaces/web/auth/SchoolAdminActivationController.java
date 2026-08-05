package com.campusguinness.interfaces.web.auth;

import com.campusguinness.identity.application.service.ActivateSchoolAdminCommand;
import com.campusguinness.identity.application.service.SchoolAdminActivationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/school-admin")
public class SchoolAdminActivationController {

    private final SchoolAdminActivationService service;

    public SchoolAdminActivationController(SchoolAdminActivationService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateSchoolAdminRequest request) {
        service.activate(new ActivateSchoolAdminCommand(
                request.username(),
                request.invitationCode(),
                request.newPassword(),
                request.confirmPassword()
        ));
        return ResponseEntity.noContent().build();
    }
}
