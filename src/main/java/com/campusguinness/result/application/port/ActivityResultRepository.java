package com.campusguinness.result.application.port;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import java.util.Optional;
public interface ActivityResultRepository {
    void save(ActivityResult activityResult);
    Optional<ActivityResult> findById(ActivityResultId id);
}
