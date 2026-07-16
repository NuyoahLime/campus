package com.campusguinness.appeal.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ScoreAppealJpaRepository extends JpaRepository<ScoreAppealEntity, UUID> { }
