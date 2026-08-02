package com.campusguinness.interfaces.web.activity;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolAdminParticipantControllerIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId;
    UUID otherSchoolId;
    UUID adminId;
    UUID otherAdminId;
    UUID studentId;
    UUID otherStudentId;
    UUID teacherId;
    UUID studentMembershipId;
    UUID otherStudentMembershipId;
    UUID activityId;
    UUID otherActivityId;
    UUID projectId;
    UUID ruleVersionId;
    UUID activityProjectId;
    String studentUsername;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        otherAdminId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        otherStudentId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        studentMembershipId = UUID.randomUUID();
        otherStudentMembershipId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        otherActivityId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        activityProjectId = UUID.randomUUID();
        studentUsername = "roster-student-" + suffix();

        insertSchool(schoolId, "Roster School");
        insertSchool(otherSchoolId, "Other Roster School");
        insertUser(adminId, "roster-admin");
        insertUser(otherAdminId, "other-roster-admin");
        insertUserWithUsername(studentId, studentUsername);
        insertUser(otherStudentId, "other-roster-student");
        insertUser(teacherId, "roster-teacher");
        insertMembership(UUID.randomUUID(), adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        insertMembership(UUID.randomUUID(), otherAdminId, otherSchoolId, "SCHOOL_ADMIN", "ACTIVE");
        insertMembership(studentMembershipId, studentId, schoolId, "STUDENT", "ACTIVE");
        insertMembership(otherStudentMembershipId, otherStudentId, otherSchoolId, "STUDENT", "ACTIVE");
        insertMembership(UUID.randomUUID(), teacherId, schoolId, "TEACHER", "ACTIVE");
        insertActivity(activityId, schoolId, adminId, "Own Activity");
        insertActivity(otherActivityId, otherSchoolId, otherAdminId, "Other Activity");

        jdbc.update("""
                INSERT INTO challenge_projects(
                    id,name,category,score_storage_type,score_indicator_type,
                    comparison_direction,allow_tie,effective_score_rule,project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, projectId, "Roster Project", "SPEED", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", true, "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id,project_id,version_number,score_storage_type,score_indicator_type,
                    comparison_direction,effective_score_rule,created_by
                ) VALUES (?,?,?,?,?,?,?,?)
                """, ruleVersionId, projectId, 1, "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", adminId);
        jdbc.update("""
                INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id)
                VALUES (?,?,?,?)
                """, activityProjectId, activityId, projectId, ruleVersionId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM score_attempts WHERE activity_project_id=?", activityProjectId);
        jdbc.update("DELETE FROM activity_project_participants WHERE activity_project_id=?", activityProjectId);
        jdbc.update("DELETE FROM activity_participants WHERE activity_id IN (?,?)", activityId, otherActivityId);
        jdbc.update("DELETE FROM activity_projects WHERE id=?", activityProjectId);
        jdbc.update("DELETE FROM activities WHERE id IN (?,?)", activityId, otherActivityId);
        jdbc.update("DELETE FROM project_rule_versions WHERE id=?", ruleVersionId);
        jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?,?,?,?,?)",
                adminId, otherAdminId, studentId, otherStudentId, teacherId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?,?,?)",
                adminId, otherAdminId, studentId, otherStudentId, teacherId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
    }

    @Test
    void schoolAdminCanListOwnActivityParticipants() throws Exception {
        insertParticipant(UUID.randomUUID(), activityId, studentMembershipId);

        mvc.perform(get(activityParticipantsUrl(activityId))
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value(studentUsername));
    }

    @Test
    void schoolAdminCannotListOtherSchoolActivityParticipants() throws Exception {
        insertParticipant(UUID.randomUUID(), otherActivityId, otherStudentMembershipId);

        mvc.perform(get(activityParticipantsUrl(otherActivityId))
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminCanAddOwnSchoolStudent() throws Exception {
        mvc.perform(post(activityParticipantsUrl(activityId))
                        .with(csrf())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()));

        assertThat(participantCount(activityId, studentMembershipId)).isEqualTo(1);
    }

    @Test
    void schoolAdminCannotAddOtherSchoolStudent() throws Exception {
        mvc.perform(post(activityParticipantsUrl(activityId))
                        .with(csrf())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + otherStudentId + "\"}"))
                .andExpect(status().isNotFound());

        assertThat(participantCount(activityId, otherStudentMembershipId)).isZero();
    }

    @Test
    void schoolAdminCanRemoveUnassignedParticipant() throws Exception {
        insertParticipant(UUID.randomUUID(), activityId, studentMembershipId);

        mvc.perform(delete(activityParticipantsUrl(activityId) + "/" + studentId)
                        .with(csrf())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(participantCount(activityId, studentMembershipId)).isZero();
    }

    @Test
    void schoolAdminCanListProjectParticipants() throws Exception {
        UUID participantId = UUID.randomUUID();
        insertParticipant(participantId, activityId, studentMembershipId);
        insertProjectParticipant(participantId);

        mvc.perform(get(projectParticipantsUrl())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].displayName").value(studentUsername));
    }

    @Test
    void schoolAdminCanAssignParticipantToProject() throws Exception {
        UUID participantId = UUID.randomUUID();
        insertParticipant(participantId, activityId, studentMembershipId);

        mvc.perform(post(projectParticipantsUrl())
                        .with(csrf())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()));

        assertThat(projectParticipantCount(participantId)).isEqualTo(1);
    }

    @Test
    void schoolAdminCanUnassignParticipantWithoutScore() throws Exception {
        UUID participantId = UUID.randomUUID();
        insertParticipant(participantId, activityId, studentMembershipId);
        insertProjectParticipant(participantId);

        mvc.perform(delete(projectParticipantsUrl() + "/" + studentId)
                        .with(csrf())
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(projectParticipantCount(participantId)).isZero();
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get(activityParticipantsUrl(activityId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotUseSchoolAdminMutationEndpoint() throws Exception {
        mvc.perform(post(activityParticipantsUrl(activityId))
                        .with(csrf())
                        .with(authUser(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPageReturns400() throws Exception {
        mvc.perform(get(activityParticipantsUrl(activityId) + "?page=-1")
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSizeReturns400() throws Exception {
        mvc.perform(get(activityParticipantsUrl(activityId) + "?size=101")
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlongKeywordReturns400() throws Exception {
        mvc.perform(get(activityParticipantsUrl(activityId) + "?keyword=" + "A".repeat(101))
                        .with(authUser(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private void insertSchool(UUID id, String name) {
        jdbc.update("""
                INSERT INTO schools(
                    id,name,unified_code_type,unified_code,internal_code,school_type,
                    region,address,contact_name,contact_phone,contact_email,school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, name, "USCC", "UC-" + suffix(), "INT-" + suffix(),
                "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
    }

    private void insertUser(UUID id, String prefix) {
        insertUserWithUsername(id, prefix + "-" + suffix());
    }

    private void insertUserWithUsername(UUID id, String username) {
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, id, username, "hash", "NORMAL");
    }

    private void insertMembership(UUID id, UUID userId, UUID targetSchoolId,
                                  String role, String membershipStatus) {
        jdbc.update("""
                INSERT INTO school_memberships(
                    id,user_id,school_id,role_in_school,status,started_at,created_at,version
                ) VALUES (?,?,?,?,?,now(),now(),1)
                """, id, userId, targetSchoolId, role, membershipStatus);
    }

    private void insertActivity(UUID id, UUID targetSchoolId, UUID createdBy, String title) {
        jdbc.update("""
                INSERT INTO activities(
                    id,school_id,title,execution_status,public_status,created_by,
                    created_at,updated_at,version
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, id, targetSchoolId, title, "DRAFT", "NOT_SUBMITTED", createdBy,
                ts(Instant.now()), ts(Instant.now()), 1);
    }

    private void insertParticipant(UUID participantId, UUID targetActivityId, UUID membershipId) {
        jdbc.update("""
                INSERT INTO activity_participants(id,activity_id,student_membership_id,created_at)
                VALUES (?,?,?,?)
                """, participantId, targetActivityId, membershipId, ts(Instant.now()));
    }

    private void insertProjectParticipant(UUID participantId) {
        jdbc.update("""
                INSERT INTO activity_project_participants(
                    id,activity_project_id,activity_participant_id,assigned_by,assigned_at
                ) VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), activityProjectId, participantId, adminId, ts(Instant.now()));
    }

    private int participantCount(UUID targetActivityId, UUID membershipId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_participants
                WHERE activity_id=? AND student_membership_id=?
                """, Integer.class, targetActivityId, membershipId);
    }

    private int projectParticipantCount(UUID participantId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_project_participants
                WHERE activity_project_id=? AND activity_participant_id=?
                """, Integer.class, activityProjectId, participantId);
    }

    private RequestPostProcessor authUser(UUID userId, UUID targetSchoolId, String role) {
        List<SchoolMembershipRecord> memberships = targetSchoolId == null
                ? List.of()
                : List.of(new SchoolMembershipRecord(targetSchoolId, role));
        var identity = new ResolvedIdentity(userId, role, targetSchoolId, "NORMAL");
        var details = new CampusGuinnessUserDetails(
                userId,
                "test-" + userId,
                "hash",
                "NORMAL",
                
                null,
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships,
                identity);
        var authentication = new UsernamePasswordAuthenticationToken(
                details, details.getPassword(), details.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private String activityParticipantsUrl(UUID targetActivityId) {
        return "/api/v1/school-admin/activities/" + targetActivityId + "/participants";
    }

    private String projectParticipantsUrl() {
        return "/api/v1/school-admin/activities/" + activityId
                + "/projects/" + projectId + "/participants";
    }

    private static Timestamp ts(Instant value) {
        return Timestamp.from(value);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
