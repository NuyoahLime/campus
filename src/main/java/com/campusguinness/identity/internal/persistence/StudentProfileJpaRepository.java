package com.campusguinness.identity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentProfileJpaRepository extends JpaRepository<StudentProfileEntity, UUID> {

    boolean existsByMembershipId(UUID membershipId);
}
