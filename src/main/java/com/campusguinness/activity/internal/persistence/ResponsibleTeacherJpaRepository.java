package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ResponsibleTeacherJpaRepository extends JpaRepository<ResponsibleTeacherEntity, UUID> {
    List<ResponsibleTeacherEntity> findByActivityProjectId(UUID activityProjectId);
    boolean existsByActivityProjectIdAndTeacherMembershipId(UUID activityProjectId, UUID teacherMembershipId);
    void deleteByActivityProjectIdAndTeacherMembershipId(UUID activityProjectId, UUID teacherMembershipId);
}
