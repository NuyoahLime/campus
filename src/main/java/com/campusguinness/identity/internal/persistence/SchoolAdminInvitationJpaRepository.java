package com.campusguinness.identity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolAdminInvitationJpaRepository extends JpaRepository<SchoolAdminInvitationEntity, UUID> {
}
