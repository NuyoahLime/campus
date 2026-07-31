package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.service.StudentAchievementApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/achievement-records")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAchievementController {

    private final StudentAchievementApplicationService service;
    private final CurrentActor currentActor;

    public StudentAchievementController(
            StudentAchievementApplicationService service,
            CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @GetMapping
    public PageResponse<AchievementRecordItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.list(
                currentActor.requireUserId(), status, keyword, page, size);
        return PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{recordId}")
    public AchievementRecordDetail get(@PathVariable UUID recordId) {
        return service.get(currentActor.requireUserId(), recordId);
    }
}
