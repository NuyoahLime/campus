package com.campusguinness.school.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SchoolRegistrationJpaRepository extends JpaRepository<SchoolRegistrationEntity, UUID> {
}
