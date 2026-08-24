package com.campusguinness.interfaces.web.activity;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ActivityParticipantScopeIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String prefix = "s25p-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID adminAMembership;
    private UUID adminBMembership;
    private UUID studentA;
    private UUID studentB;
    private UUID studentAMembership;
    private UUID studentBMembership;
    private UUID inactiveStudent;
    private UUID nonStudent;
    private UUID superAdmin;
    private UUID activityA;
    private UUID activityB;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        studentB = insertUser("student-b", null);
        inactiveStudent = insertUser("inactive-student", null);
        nonStudent = insertUser("non-student", null);
        superAdmin = insertUser("super-admin", "SUPER_ADMIN");

        adminAMembership = insertMembership(adminA, schoolA, "SCHOOL_ADMIN", "ACTIVE");
        adminBMembership = insertMembership(adminB, schoolB, "SCHOOL_ADMIN", "ACTIVE");
        studentAMembership = insertMembership(studentA, schoolA, "STUDENT", "ACTIVE");
        studentBMembership = insertMembership(studentB, schoolB, "STUDENT", "ACTIVE");
        insertMembership(inactiveStudent, schoolA, "STUDENT", "ENDED");
        insertMembership(nonStudent, schoolA, "SCHOOL_ADMIN", "ACTIVE");
        insertStudentProfile(studentAMembership, "A-001");
        insertStudentProfile(studentBMembership, "B-001");

        activityA = insertActivity(schoolA, adminA, "PUBLISHED", "a");
        activityB = insertActivity(schoolB, adminB, "PUBLISHED", "b");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM activity_participants
                WHERE activity_id IN (
                    SELECT a.id FROM activities a
                    JOIN schools s ON s.id = a.school_id
                    WHERE s.name LIKE ?
                )
                """, prefix + "%");
        jdbc.update("""
                DELETE FROM student_profiles
                WHERE membership_id IN (
                    SELECT sm.id FROM school_memberships sm
                    JOIN users u ON u.id = sm.user_id
                    WHERE u.username LIKE ?
                )
                """, prefix + "%");
        jdbc.update("""
                DELETE FROM activities
                WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)
                """, prefix + "%");
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                """, prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", prefix + "%");
    }

    @Test
    void sameSchoolAdminCanViewCandidatesAddListAndRemoveParticipants() throws Exception {
        mvc.perform(get(activityPath(activityA) + "/participant-candidates").with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].studentId", hasItem(studentA.toString())));

        mvc.perform(assign(activityA, studentA).with(adminA()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(participantsPath(activityA)).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentA.toString()));
        assertThat(participantCount(activityA, studentAMembership)).isEqualTo(1);

        mvc.perform(delete(participantsPath(activityA) + "/" + studentA).with(adminA()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(participantsPath(activityA)).with(adminA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(participantCount(activityA, studentAMembership)).isZero();
    }

    @Test
    void sequentialDuplicateAssignmentReturnsTheParticipantConflict() throws Exception {
        mvc.perform(assign(activityA, studentA).with(adminA()).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(assign(activityA, studentA).with(adminA()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_PARTICIPANT_ALREADY_ASSIGNED"));
        assertThat(participantCount(activityA, studentAMembership)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateAssignmentsProduceOneWriteAndOneControlledConflict() throws Exception {
        UUID concurrentActivity = insertActivity(schoolA, adminA, "PUBLISHED", "concurrent");

        List<Integer> statuses = runConcurrently(
                () -> mvc.perform(assign(concurrentActivity, studentA).with(adminA()).with(csrf()))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(assign(concurrentActivity, studentA).with(adminA()).with(csrf()))
                        .andReturn().getResponse().getStatus()
        );

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(participantCount(concurrentActivity, studentAMembership)).isEqualTo(1);
    }

    @Test
    void crossSchoolAdminCannotReadAddOrRemoveParticipants() throws Exception {
        insertParticipant(activityB, studentBMembership);

        mvc.perform(get(participantsPath(activityB)).with(adminA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));
        mvc.perform(assign(activityB, studentB).with(adminA()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));
        mvc.perform(delete(participantsPath(activityB) + "/" + studentB).with(adminA()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));
        assertThat(participantCount(activityB, studentBMembership)).isEqualTo(1);
    }

    @Test
    void rejectsCrossSchoolInactiveAndNonStudentTargetsWithoutWritingAssignments() throws Exception {
        mvc.perform(assign(activityA, studentB).with(adminA()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        mvc.perform(assign(activityA, inactiveStudent).with(adminA()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        mvc.perform(assign(activityA, nonStudent).with(adminA()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        assertThat(participantCountForActivity(activityA)).isZero();
    }

    @Test
    void studentAssignmentLifecycleControlsTheStudentListAndDetail() throws Exception {
        mvc.perform(get("/api/v1/student/activities/{id}", activityA).with(studentA()))
                .andExpect(status().isNotFound());

        mvc.perform(assign(activityA, studentA).with(adminA()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/student/activities").with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(activityA.toString())));
        mvc.perform(get("/api/v1/student/activities/{id}", activityA).with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activityA.toString()));

        mvc.perform(delete(participantsPath(activityA) + "/" + studentA).with(adminA()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/student/activities").with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/student/activities/{id}", activityA).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentDetailConcealsUnassignedAndOtherSchoolActivities() throws Exception {
        mvc.perform(get("/api/v1/student/activities/{id}", activityA).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/activities/{id}", activityB).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentReadIncludesOnlyAssignedParticipantVisibleLifecycleStates() throws Exception {
        UUID inProgress = insertActivity(schoolA, adminA, "IN_PROGRESS", "in-progress");
        UUID ended = insertActivity(schoolA, adminA, "ENDED", "ended");
        UUID draft = insertActivity(schoolA, adminA, "DRAFT", "draft");
        UUID cancelled = insertActivity(schoolA, adminA, "CANCELLED", "cancelled");
        for (UUID id : List.of(activityA, inProgress, ended, draft, cancelled)) {
            insertParticipant(id, studentAMembership);
        }

        mvc.perform(get("/api/v1/student/activities").with(studentA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[*].id", hasItem(activityA.toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(inProgress.toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(ended.toString())));
        mvc.perform(get("/api/v1/student/activities/{id}", draft).with(studentA()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/activities/{id}", cancelled).with(studentA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void securityFilterAndLiveMembershipChecksProtectParticipantEndpoints() throws Exception {
        mvc.perform(get(participantsPath(activityA))).andExpect(status().isUnauthorized());
        mvc.perform(assign(activityA, studentA).with(studentA()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(assign(activityA, studentA).with(superAdmin()).with(csrf())).andExpect(status().isForbidden());

        jdbc.update("UPDATE school_memberships SET status = 'ENDED', ended_at = now() WHERE id = ?", adminAMembership);
        mvc.perform(get(participantsPath(activityA)).with(adminA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));

        insertMembership(adminB, schoolA, "SCHOOL_ADMIN", "ACTIVE");
        mvc.perform(get(participantsPath(activityB)).with(adminB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));

        jdbc.update("UPDATE school_memberships SET status = 'ENDED', ended_at = now() WHERE id = ?", studentAMembership);
        mvc.perform(get("/api/v1/student/activities").with(studentA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));

        insertMembership(studentB, schoolA, "STUDENT", "ACTIVE");
        mvc.perform(get("/api/v1/student/activities").with(studentB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
    }

    private List<Integer> runConcurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(first, second).stream()
                    .map(call -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return call.call();
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(30, TimeUnit.SECONDS),
                    results.get(1).get(30, TimeUnit.SECONDS));
            return statuses;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private RequestPostProcessor adminA() {
        return principal("SCHOOL_ADMIN", adminA, adminAMembership, schoolA);
    }

    private RequestPostProcessor adminB() {
        return principal("SCHOOL_ADMIN", adminB, adminBMembership, schoolB);
    }

    private RequestPostProcessor studentA() {
        return principal("STUDENT", studentA, studentAMembership, schoolA);
    }

    private RequestPostProcessor studentB() {
        return principal("STUDENT", studentB, studentBMembership, schoolB);
    }

    private RequestPostProcessor superAdmin() {
        var details = new CampusGuinnessUserDetails(superAdmin, prefix + "-super-admin", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")), List.of());
        return user(details);
    }

    private RequestPostProcessor principal(String role, UUID userId, UUID membershipId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(userId, prefix + "-" + role.toLowerCase(), "{noop}password",
                "NORMAL", Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, role)));
        return user(details);
    }

    private String participantsPath(UUID activityId) {
        return "/api/v1/school-admin/activities/" + activityId + "/participants";
    }

    private String activityPath(UUID activityId) {
        return "/api/v1/school-admin/activities/" + activityId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder assign(
            UUID activityId,
            UUID studentId
    ) {
        return post(participantsPath(activityId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}");
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,
                                    address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix + "-school-" + label, "USCC", prefix + "-uc-" + suffix, prefix + "-ic-" + suffix,
                "UNIVERSITY", "Region", "Address", "Contact", "13800000000", prefix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                id, prefix + "-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL",
                platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status)
                VALUES (?,?,?,?,?)
                """, id, userId, schoolId, role, status);
        return id;
    }

    private void insertStudentProfile(UUID membershipId, String studentNumber) {
        jdbc.update("""
                INSERT INTO student_profiles(id,membership_id,grade,class_name,student_number)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), membershipId, "2026", "Class A", studentNumber);
    }

    private UUID insertActivity(UUID schoolId, UUID createdBy, String executionStatus, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by)
                VALUES (?,?,?,?,?,?)
                """, id, schoolId, prefix + "-activity-" + label, executionStatus, "NOT_SUBMITTED", createdBy);
        return id;
    }

    private void insertParticipant(UUID activityId, UUID membershipId) {
        jdbc.update("""
                INSERT INTO activity_participants(id,activity_id,student_membership_id,created_at)
                VALUES (?,?,?,now())
                """, UUID.randomUUID(), activityId, membershipId);
    }

    private int participantCount(UUID activityId, UUID membershipId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_participants
                WHERE activity_id = ? AND student_membership_id = ?
                """, Integer.class, activityId, membershipId);
    }

    private int participantCountForActivity(UUID activityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activity_participants WHERE activity_id = ?",
                Integer.class, activityId);
    }
}
