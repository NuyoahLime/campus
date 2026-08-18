package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ActivityProjectJpaRepository extends JpaRepository<ActivityProjectEntity, UUID> {}
