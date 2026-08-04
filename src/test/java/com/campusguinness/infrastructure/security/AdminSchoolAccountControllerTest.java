package com.campusguinness.infrastructure.security;

import com.campusguinness.interfaces.web.account.AdminSchoolAccountController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSchoolAccountControllerTest {

    @Mock AccountProvisioningService service;
    @Mock CurrentActor currentActor;
    AdminSchoolAccountController controller;

    private static final UUID ACTOR_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID SCHOOL_ID = UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("c3333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        controller = new AdminSchoolAccountController(service, currentActor);
        lenient().when(currentActor.requireUserId()).thenReturn(ACTOR_ID);
    }

    @Test
    void createSchoolAdminReturnsGeneratedTemporaryPassword() {
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "admin1", "SCHOOL_ADMIN", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "xYz123Abc456Def789");

        when(service.createSchoolAdmin(eq(ACTOR_ID), eq(SCHOOL_ID), eq("admin1")))
                .thenReturn(result);

        var req = new AdminSchoolAccountController.CreateRequest("admin1");
        ResponseEntity<Map<String, Object>> resp = controller.createSchoolAdmin(SCHOOL_ID, req);

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("userId", USER_ID);
        assertThat(resp.getBody()).containsEntry("temporaryPassword", "xYz123Abc456Def789");
        assertThat(resp.getBody()).containsEntry("accountStatus", "PENDING_ACTIVATION");
    }

    @Test
    void createSchoolAdminResponseUsesNoStore() {
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "admin1", "SCHOOL_ADMIN", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "xYz123Abc456Def789");

        when(service.createSchoolAdmin(any(), any(), any())).thenReturn(result);

        var req = new AdminSchoolAccountController.CreateRequest("admin1");
        ResponseEntity<Map<String, Object>> resp = controller.createSchoolAdmin(SCHOOL_ID, req);

        assertThat(resp.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
        assertThat(resp.getHeaders().get("Pragma")).contains("no-cache");
    }

    @Test
    void createSchoolAdminDoesNotPassClientPasswordToService() {
        // The CreateRequest has NO temporaryPassword field — verify
        // the service method receives only 3 args (no password)
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "admin1", "SCHOOL_ADMIN", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "generated");
        when(service.createSchoolAdmin(eq(ACTOR_ID), eq(SCHOOL_ID), eq("admin1")))
                .thenReturn(result);

        var req = new AdminSchoolAccountController.CreateRequest("admin1");
        controller.createSchoolAdmin(SCHOOL_ID, req);

        // Verify the 3-arg method was called exactly once
        verify(service).createSchoolAdmin(ACTOR_ID, SCHOOL_ID, "admin1");
    }

    @Test
    void listSchoolAdminsDoesNotExposeTemporaryPassword() {
        var item = new AccountProvisioningService.AccountItem(
                USER_ID, "admin1", "SCHOOL_ADMIN", "Test School",
                "PENDING_ACTIVATION", java.time.Instant.now());
        when(service.listSchoolAdmins(eq(SCHOOL_ID), eq(0), eq(100)))
                .thenReturn(List.of(item));

        List<Map<String, Object>> list = controller.listSchoolAdmins(SCHOOL_ID);

        assertThat(list).hasSize(1);
        // AccountItem has no temporaryPassword field — the Map must not contain it
        assertThat(list.get(0)).doesNotContainKey("temporaryPassword");
        assertThat(list.get(0)).containsKeys("userId", "username", "role", "schoolName", "accountStatus", "createdAt");
    }
}
