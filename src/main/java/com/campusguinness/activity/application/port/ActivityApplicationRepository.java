package com.campusguinness.activity.application.port;
import com.campusguinness.activity.internal.domain.ActivityApplication;
import com.campusguinness.activity.internal.domain.ActivityApplicationId;
import java.util.Optional;
public interface ActivityApplicationRepository {
    void save(ActivityApplication application);
    Optional<ActivityApplication> findById(ActivityApplicationId id);
}
