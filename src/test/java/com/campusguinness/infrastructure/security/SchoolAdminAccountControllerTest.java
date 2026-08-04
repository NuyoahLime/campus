package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.interfaces.web.account.SchoolAdminAccountController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolAdminAccountControllerTest {

    @Mock AccountProvisioningService service;
    @Mock CurrentActor currentActor;
    @Mock SchoolMembershipQueryPort membershipPort;
    SchoolAdminAccountController controller;

    private static final UUID ACTOR_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID SCHOOL_ID = UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("c3333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        controller = new SchoolAdminAccountController(service, currentActor, membershipPort);
        lenient().when(currentActor.requireUserId()).thenReturn(ACTOR_ID);
        lenient().when(membershipPort.findActiveSchoolAdminSchoolId(ACTOR_ID))
                .thenReturn(Optional.of(SCHOOL_ID));
    }

    @Test
    void createTeacherStudentReturnsGeneratedTemporaryPassword() {
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "student1", "STUDENT", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "xYz123Abc456Def789");

        when(service.createTeacherOrStudent(eq(ACTOR_ID), eq(SCHOOL_ID), eq("student1"), eq("STUDENT")))
                .thenReturn(result);

        var req = new SchoolAdminAccountController.CreateAccountRequest("student1", "STUDENT");
        ResponseEntity<Map<String, Object>> resp = controller.createAccount(req);

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("temporaryPassword", "xYz123Abc456Def789");
        assertThat(resp.getBody()).containsEntry("role", "STUDENT");
        assertThat(resp.getBody()).containsEntry("accountStatus", "PENDING_ACTIVATION");
    }

    @Test
    void createTeacherStudentResponseUsesNoStore() {
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "student1", "STUDENT", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "generated");
        when(service.createTeacherOrStudent(any(), any(), any(), any())).thenReturn(result);

        var req = new SchoolAdminAccountController.CreateAccountRequest("student1", "STUDENT");
        ResponseEntity<Map<String, Object>> resp = controller.createAccount(req);

        assertThat(resp.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
        assertThat(resp.getHeaders().get("Pragma")).contains("no-cache");
    }

    @Test
    void createTeacherStudentDerivesSchoolFromCurrentAdmin() {
        var result = new AccountProvisioningService.ProvisioningResult(
                USER_ID, "student1", "STUDENT", SCHOOL_ID, "Test School",
                "PENDING_ACTIVATION", "generated");
        when(service.createTeacherOrStudent(eq(ACTOR_ID), eq(SCHOOL_ID), eq("student1"), eq("STUDENT")))
                .thenReturn(result);

        var req = new SchoolAdminAccountController.CreateAccountRequest("student1", "STUDENT");
        controller.createAccount(req);

        verify(membershipPort).findActiveSchoolAdminSchoolId(ACTOR_ID);
        verify(service).createTeacherOrStudent(ACTOR_ID, SCHOOL_ID, "student1", "STUDENT");
    }

    @Test
    void listAccountsDoesNotExposeTemporaryPassword() {
        var item = new AccountProvisioningService.AccountItem(
                USER_ID, "student1", "STUDENT", "Test School",
                "PENDING_ACTIVATION", java.time.Instant.now());
        when(service.listSchoolAccounts(eq(SCHOOL_ID), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(item));

        List<Map<String, Object>> list = controller.listAccounts(null, null, null, 0, 20);

        assertThat(list).hasSize(1);
        assertThat(list.get(0)).doesNotContainKey("temporaryPassword");
        assertThat(list.get(0)).containsKeys("userId", "username", "role", "schoolName", "accountStatus", "createdAt");
    }

    @Test
    void createAccountRequestHasNoTemporaryPasswordField() {
        // Verify the DTO record structure via reflection — only username and role
        var fields = SchoolAdminAccountController.CreateAccountRequest.class.getRecordComponents();
        var names = java.util.Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(names).containsExactlyInAnyOrder("username", "role");
        assertThat(names).doesNotContain("temporaryPassword");
    }
}
