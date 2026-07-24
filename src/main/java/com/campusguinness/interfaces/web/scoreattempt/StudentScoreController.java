package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.StudentScoreDetail;
import com.campusguinness.score.application.query.model.StudentScoreItem;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentScoreController {

    private final StudentScoreQueryPort scoreQueryPort;
    private final CurrentActor currentActor;

    public StudentScoreController(StudentScoreQueryPort scoreQueryPort, CurrentActor currentActor) {
        this.scoreQueryPort = scoreQueryPort;
        this.currentActor = currentActor;
    }

    @GetMapping("/scores")
    public ResponseEntity<QueryPage<StudentScoreItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        UUID studentId = currentActor.requireUserId();
        return ResponseEntity.ok(scoreQueryPort.findByStudentId(studentId, status, activityId, projectId, page, size));
    }

    @GetMapping("/scores/{attemptId}")
    public ResponseEntity<StudentScoreDetail> getDetail(@PathVariable UUID attemptId) {
        UUID studentId = currentActor.requireUserId();
        return scoreQueryPort.findByIdAndStudentId(attemptId, studentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
