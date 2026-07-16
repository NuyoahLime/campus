package com.campusguinness.activity.application.port;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import java.util.Optional;
public interface ActivityRepository {
    void save(Activity activity);
    Optional<Activity> findById(ActivityId id);
}
