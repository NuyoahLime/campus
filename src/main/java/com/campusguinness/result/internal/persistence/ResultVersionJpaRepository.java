package com.campusguinness.result.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ResultVersionJpaRepository extends JpaRepository<ResultVersionEntity, UUID> {
    boolean existsByIdAndResultId(UUID id, UUID resultId);
}
