package com.campusguinness.interfaces.web.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.campusguinness.infrastructure.security.AccountActivationService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountActivationController {

    private final AccountActivationService service;

    public AccountActivationController(AccountActivationService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activate(@Valid @RequestBody ActivateRequest req, HttpServletRequest request) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("code","PASSWORD_MISMATCH","message","两次输入的密码不一致"));
        }
        if (req.newPassword().equals(req.temporaryPassword())) {
            return ResponseEntity.badRequest().body(Map.of("code","PASSWORD_SAME_AS_TEMP","message","新密码不能与临时密码相同"));
        }

        String ip = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");
        var result = service.activate(req.username(), req.temporaryPassword(), req.newPassword(), ip, ua);
        if (!result.success()) {
            int status = switch (result.code()) {
                case "ACCOUNT_ALREADY_ACTIVATED", "ACCOUNT_STATE_INVALID" -> 409;
                case "ACTIVATION_RATE_LIMITED" -> 429;
                default -> 401;
            };
            return ResponseEntity.status(status).body(Map.of("code", result.code(), "message", result.message()));
        }
        return ResponseEntity.ok(Map.of("message", result.message()));
    }

    public record ActivateRequest(@NotBlank String username, @NotBlank String temporaryPassword, @NotBlank @Size(min=8) String newPassword, @NotBlank String confirmPassword) {}
}
