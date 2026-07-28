package com.campusguinness.identity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolTeacherDirectoryQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired SchoolTeacherDirectoryQueryAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId, otherSchoolId;
    UUID teacherId, otherTeacherId, inactiveTeacherId, lockedTeacherId;
    final List<UUID> createdSchoolIds = new ArrayList<>();
    final List<UUID> createdUserIds = new ArrayList<>();
    final List<UUID> createdMembershipIds = new ArrayList<>();

    @BeforeEach void setUp() {
        schoolId = UUID.randomUUID(); otherSchoolId = UUID.randomUUID();
        teacherId = UUID.randomUUID(); otherTeacherId = UUID.randomUUID();
        inactiveTeacherId = UUID.randomUUID(); lockedTeacherId = UUID.randomUUID();

        for (UUID sid : List.of(schoolId, otherSchoolId)) {
            createdSchoolIds.add(sid);
            jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    sid, "School-" + sid.toString().substring(0,6), "USCC", "UC-" + sid.toString().substring(0,6), "INT-" + sid.toString().substring(0,6), "PRIMARY", "Beijing", "addr", "n", "p", "e", "NORMAL");
        }

        // Active teachers at schoolId
        createdUserIds.add(teacherId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", teacherId, "teacher1", "hash", "NORMAL");
        UUID m1 = UUID.randomUUID(); createdMembershipIds.add(m1);
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", m1, teacherId, schoolId, "TEACHER", "ACTIVE");
        jdbc.update("INSERT INTO teacher_profiles(membership_id,subject,title) VALUES (?,?,?)", m1, "Math", "Senior Teacher");

        createdUserIds.add(otherTeacherId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", otherTeacherId, "other_teacher", "hash", "NORMAL");
        UUID m2 = UUID.randomUUID(); createdMembershipIds.add(m2);
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", m2, otherTeacherId, otherSchoolId, "TEACHER", "ACTIVE");

        // Inactive membership (same school, different teacher)
        createdUserIds.add(inactiveTeacherId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", inactiveTeacherId, "inactive_t", "hash", "NORMAL");
        UUID m3 = UUID.randomUUID(); createdMembershipIds.add(m3);
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", m3, inactiveTeacherId, schoolId, "TEACHER", "ENDED");

        // Locked account
        createdUserIds.add(lockedTeacherId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", lockedTeacherId, "locked_t", "hash", "LOCKED");
        UUID m4 = UUID.randomUUID(); createdMembershipIds.add(m4);
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", m4, lockedTeacherId, schoolId, "TEACHER", "ACTIVE");
    }

    @AfterEach void tearDown() {
        jdbc.update("DELETE FROM teacher_profiles WHERE membership_id IN (" + String.join(",", createdMembershipIds.stream().map(id -> "'" + id + "'").toArray(String[]::new)) + ")");
        for (UUID mid : createdMembershipIds) { jdbc.update("DELETE FROM school_memberships WHERE id=?", mid); }
        for (UUID uid : createdUserIds) { jdbc.update("DELETE FROM users WHERE id=?", uid); }
        for (UUID sid : createdSchoolIds) { jdbc.update("DELETE FROM schools WHERE id=?", sid); }
    }

    @Test void filtersToSchool() {
        var result = adapter.findActiveTeachers(schoolId, null, 0, 20);
        assertThat(result.items().stream().map(i -> i.userId())).contains(teacherId);
        assertThat(result.items().stream().map(i -> i.userId())).doesNotContain(otherTeacherId, inactiveTeacherId, lockedTeacherId);
    }

    @Test void excludesInactiveMembershipAndLockedAccount() {
        var result = adapter.findActiveTeachers(schoolId, null, 0, 20);
        var ids = result.items().stream().map(i -> i.userId()).toList();
        assertThat(ids).doesNotContain(inactiveTeacherId, lockedTeacherId);
    }

    @Test void keywordMatchesUsername() {
        var result = adapter.findActiveTeachers(schoolId, "teacher1", 0, 20);
        assertThat(result.items().stream().map(i -> i.userId())).contains(teacherId);
    }

    @Test void keywordMatchesSubject() {
        var result = adapter.findActiveTeachers(schoolId, "math", 0, 20);
        assertThat(result.items().stream().map(i -> i.userId())).contains(teacherId);
    }

    @Test void keywordMatchesTitle() {
        var result = adapter.findActiveTeachers(schoolId, "senior", 0, 20);
        assertThat(result.items().stream().map(i -> i.userId())).contains(teacherId);
    }

    @Test void paginates() {
        var p0 = adapter.findActiveTeachers(schoolId, null, 0, 1);
        var p1 = adapter.findActiveTeachers(schoolId, null, 1, 1);
        assertThat(p0.items()).hasSize(1);
        assertThat(p1.items()).isEmpty();
        assertThat(p0.totalElements()).isEqualTo(1);
    }
}
