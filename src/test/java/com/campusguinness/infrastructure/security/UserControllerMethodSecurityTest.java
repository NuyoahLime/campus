package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.result.UserResult;
import com.campusguinness.identity.application.service.UserApplicationService;
import com.campusguinness.interfaces.web.user.CreateUserRequest;
import com.campusguinness.interfaces.web.user.UserController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Proves that {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} on {@link UserController}
 * is enforced by Spring AOP — the proxy rejects calls before they reach the service.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserControllerMethodSecurityTest {

    @Autowired
    UserController controller;

    @MockitoBean
    UserApplicationService service;

    // ── Normal user (no SUPER_ADMIN role) ──

    @Test
    @WithMockUser(username = "normal-user", roles = "USER")
    void normalUserCreateIsRejectedBeforeServiceInvocation() {
        var req = new CreateUserRequest("new-user", "pass12345678");

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "normal-user", roles = "USER")
    void normalUserActivateIsRejectedBeforeServiceInvocation() {
        assertThatThrownBy(() -> controller.activate(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "normal-user", roles = "USER")
    void normalUserDisableIsRejectedBeforeServiceInvocation() {
        assertThatThrownBy(() -> controller.disable(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "normal-user", roles = "USER")
    void normalUserReEnableIsRejectedBeforeServiceInvocation() {
        assertThatThrownBy(() -> controller.reEnable(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    // ── SUPER_ADMIN ──

    @Test
    @WithMockUser(username = "admin", roles = "SUPER_ADMIN")
    void superAdminCreatePassesMethodSecurity() {
        UUID id = UUID.randomUUID();
        when(service.create(anyString(), anyString()))
                .thenReturn(new UserResult(id, "new-user", "PENDING_ACTIVATION"));

        var response = controller.create(new CreateUserRequest("new-user", "pass12345678"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("new-user");

        verify(service).create("new-user", "pass12345678");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SUPER_ADMIN")
    void superAdminActivatePassesMethodSecurity() {
        UUID id = UUID.randomUUID();
        when(service.activate(id))
                .thenReturn(new UserResult(id, "target", "NORMAL"));

        var response = controller.activate(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(service).activate(id);
    }

    @Test
    @WithMockUser(username = "admin", roles = "SUPER_ADMIN")
    void superAdminDisablePassesMethodSecurity() {
        UUID id = UUID.randomUUID();
        when(service.disable(id))
                .thenReturn(new UserResult(id, "target", "DISABLED"));

        var response = controller.disable(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(service).disable(id);
    }

    @Test
    @WithMockUser(username = "admin", roles = "SUPER_ADMIN")
    void superAdminReEnablePassesMethodSecurity() {
        UUID id = UUID.randomUUID();
        when(service.reEnable(id))
                .thenReturn(new UserResult(id, "target", "NORMAL"));

        var response = controller.reEnable(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(service).reEnable(id);
    }
}
