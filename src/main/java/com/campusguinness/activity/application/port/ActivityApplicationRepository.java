package com.campusguinness.activity.application.port;
import com.campusguinness.activity.internal.domain.ActivityApplication;
import com.campusguinness.activity.internal.domain.ActivityApplicationId;
import com.campusguinness.activity.internal.domain.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ActivityApplicationRepository {
    void save(ActivityApplication application);
    Optional<ActivityApplication> findById(ActivityApplicationId id);
    List<ActivityApplication> findBySchoolIdAndStatus(UUID schoolId, ApplicationStatus status);
}
