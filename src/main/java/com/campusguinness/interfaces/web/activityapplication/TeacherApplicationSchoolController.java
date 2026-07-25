package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.port.TeacherApplicationQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherApplicationSchoolController {

    private final TeacherApplicationQueryPort queryPort;
    private final CurrentActor currentActor;

    public TeacherApplicationSchoolController(TeacherApplicationQueryPort queryPort, CurrentActor currentActor) {
        this.queryPort = queryPort;
        this.currentActor = currentActor;
    }

    @GetMapping("/schools")
    public List<TeacherSchoolItem> getSchools() {
        return queryPort.findTeacherSchools(currentActor.requireUserId()).stream()
                .map(s -> new TeacherSchoolItem(s.schoolId(), s.schoolName())).toList();
    }

    @GetMapping("/applications/stats")
    public ResponseEntity<ApplicationStatsResponse> getStats() {
        var stats = queryPort.getStats(currentActor.requireUserId());
        return ResponseEntity.ok(new ApplicationStatsResponse(
                stats.total(), stats.draft(), stats.submitted(),
                stats.approved(), stats.rejected(), stats.withdrawn()));
    }

    public record TeacherSchoolItem(UUID schoolId, String schoolName) {}
    public record ApplicationStatsResponse(int total, int draft, int submitted,
            int approved, int rejected, int withdrawn) {}
}
