package com.campusguinness.activity.application.port;
import com.campusguinness.activity.internal.domain.ActivityApplication;
import com.campusguinness.activity.internal.domain.ActivityApplicationId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ActivityApplicationRepository {
    void save(ActivityApplication application);
    Optional<ActivityApplication> findById(ActivityApplicationId id);
    List<ActivityApplication> findByApplicantId(UUID applicantId);
    Optional<ActivityApplication> findByIdAndApplicantId(UUID id, UUID applicantId);
}
