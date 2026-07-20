package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationPolicyTest {

    private UUID schoolId;
    private UUID adminId;
    private UUID teacherId;
    private UUID studentId;
    private UUID otherUserId;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        // create users and school needed for membership resolver
        jdbc = new JdbcTemplate();
    }

    @Nested class RequireSchoolAdmin {
        @Test void rejectsNullMembership() {
            var resolver = new StubMembershipResolver(null);
            assertThatThrownBy(() -> AuthorizationPolicy.requireSchoolAdmin(resolver, adminId, schoolId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void rejectsStudent() {
            var resolver = new StubMembershipResolver("STUDENT");
            assertThatThrownBy(() -> AuthorizationPolicy.requireSchoolAdmin(resolver, adminId, schoolId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void rejectsTeacher() {
            var resolver = new StubMembershipResolver("TEACHER");
            assertThatThrownBy(() -> AuthorizationPolicy.requireSchoolAdmin(resolver, adminId, schoolId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void allowsSchoolAdmin() {
            var resolver = new StubMembershipResolver("SCHOOL_ADMIN");
            assertThatCode(() -> AuthorizationPolicy.requireSchoolAdmin(resolver, adminId, schoolId))
                    .doesNotThrowAnyException();
        }
    }

    @Nested class RequireTeacherOrAbove {
        @Test void rejectsNullMembership() {
            var resolver = new StubMembershipResolver(null);
            assertThatThrownBy(() -> AuthorizationPolicy.requireTeacherOrAbove(resolver, teacherId, schoolId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void rejectsStudent() {
            var resolver = new StubMembershipResolver("STUDENT");
            assertThatThrownBy(() -> AuthorizationPolicy.requireTeacherOrAbove(resolver, teacherId, schoolId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void allowsTeacher() {
            var resolver = new StubMembershipResolver("TEACHER");
            assertThatCode(() -> AuthorizationPolicy.requireTeacherOrAbove(resolver, teacherId, schoolId))
                    .doesNotThrowAnyException();
        }

        @Test void allowsSchoolAdmin() {
            var resolver = new StubMembershipResolver("SCHOOL_ADMIN");
            assertThatCode(() -> AuthorizationPolicy.requireTeacherOrAbove(resolver, adminId, schoolId))
                    .doesNotThrowAnyException();
        }
    }

    @Nested class RequireResourceOwner {
        @Test void rejectsMismatchedOwner() {
            assertThatThrownBy(() -> AuthorizationPolicy.requireResourceOwner(adminId, otherUserId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test void allowsMatchingOwner() {
            assertThatCode(() -> AuthorizationPolicy.requireResourceOwner(adminId, adminId))
                    .doesNotThrowAnyException();
        }
    }

    /** Stub resolver that returns a fixed role for any (userId, schoolId) pair. */
    private static class StubMembershipResolver extends SchoolMembershipResolver {
        private final String role;
        StubMembershipResolver(String role) { super(null); this.role = role; }
        @Override public java.util.Optional<String> resolveRole(UUID uid, UUID sid) {
            return java.util.Optional.ofNullable(role);
        }
    }
}
