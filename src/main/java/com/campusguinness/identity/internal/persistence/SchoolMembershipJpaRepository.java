package com.campusguinness.identity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolMembershipJpaRepository extends JpaRepository<SchoolMembershipEntity, UUID> {

    List<SchoolMembershipEntity> findAllByUserIdOrderByStartedAtAsc(UUID userId);

    List<SchoolMembershipEntity> findAllByUserIdAndStatusOrderByStartedAtAsc(UUID userId, String status);

    Optional<SchoolMembershipEntity> findByUserIdAndSchoolIdAndStatus(
            UUID userId,
            UUID schoolId,
            String status
    );

    Optional<SchoolMembershipEntity> findByUserIdAndSchoolIdAndStatusAndRoleInSchool(
            UUID userId,
            UUID schoolId,
            String status,
            String roleInSchool
    );

    boolean existsByUserIdAndSchoolIdAndStatusAndRoleInSchool(
            UUID userId,
            UUID schoolId,
            String status,
            String roleInSchool
    );

    List<SchoolMembershipEntity> findAllByUserIdAndStatusAndRoleInSchoolInOrderByStartedAtAscIdAsc(
            UUID userId,
            String status,
            Collection<String> roleInSchool
    );
}
