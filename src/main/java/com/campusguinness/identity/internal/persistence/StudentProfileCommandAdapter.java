package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.CreateStudentProfileCommand;
import com.campusguinness.identity.application.port.StudentProfileCommandPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class StudentProfileCommandAdapter implements StudentProfileCommandPort {

    private final StudentProfileJpaRepository profiles;
    private final JdbcTemplate jdbc;

    StudentProfileCommandAdapter(StudentProfileJpaRepository profiles, JdbcTemplate jdbc) {
        this.profiles = profiles;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserId(UUID userId) {
        if (userId == null) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM student_profiles sp
                JOIN school_memberships sm ON sm.id = sp.membership_id
                WHERE sm.user_id = ?
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByMembershipId(UUID membershipId) {
        return membershipId != null && profiles.existsByMembershipId(membershipId);
    }

    @Override
    @Transactional
    public void create(CreateStudentProfileCommand command) {
        profiles.save(StudentProfileEntity.create(
                command.profileId(),
                command.membershipId(),
                command.grade(),
                command.className(),
                command.studentNumber()
        ));
        profiles.flush();
    }
}
