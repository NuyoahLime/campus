package com.campusguinness.school.internal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SchoolRegistrationJpaRepository extends JpaRepository<SchoolRegistrationEntity, UUID> {
    Page<SchoolRegistrationEntity> findByRegistrationStatus(String registrationStatus, Pageable pageable);
}
