package com.campusguinness.identity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchoolMembershipJpaRepository extends JpaRepository<SchoolMembershipEntity, UUID> {
    Optional<SchoolMembershipEntity> findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
            UUID userId, UUID schoolId, String roleInSchool, String status);
}
