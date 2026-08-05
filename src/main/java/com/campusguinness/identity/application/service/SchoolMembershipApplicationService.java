package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.SchoolMembershipResult;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.UserId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SchoolMembershipApplicationService {

    private final UserRepository users;

    public SchoolMembershipApplicationService(UserRepository users) {
        this.users = users;
    }

    public SchoolMembershipResult grantStudent(UUID userId, UUID schoolId, Instant startedAt) {
        var user = findForUpdate(userId);
        var membership = user.grantStudentMembership(
                new SchoolMembershipId(UUID.randomUUID()),
                schoolId,
                startedAt
        );
        users.save(user);
        return SchoolMembershipResult.from(user.id().value(), membership);
    }

    public SchoolMembershipResult grantSchoolAdmin(UUID userId, UUID schoolId, Instant startedAt) {
        var user = findForUpdate(userId);
        var membership = user.grantSchoolAdminMembership(
                new SchoolMembershipId(UUID.randomUUID()),
                schoolId,
                startedAt
        );
        users.save(user);
        return SchoolMembershipResult.from(user.id().value(), membership);
    }

    public SchoolMembershipResult end(UUID userId, UUID schoolId, Instant endedAt) {
        var user = findForUpdate(userId);
        var membership = user.endMembership(schoolId, endedAt);
        users.save(user);
        return SchoolMembershipResult.from(user.id().value(), membership);
    }

    private com.campusguinness.identity.internal.domain.User findForUpdate(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        return users.findByIdForUpdate(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
