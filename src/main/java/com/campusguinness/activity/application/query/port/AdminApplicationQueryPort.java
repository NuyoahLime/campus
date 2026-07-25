package com.campusguinness.activity.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminApplicationQueryPort {
    QueryPage<AdminApplicationItem> findApplications(String status, UUID schoolId, String keyword,
            Instant submittedFrom, Instant submittedTo, String sort, int page, int size);

    Optional<AdminApplicationDetail> findById(UUID applicationId);

    record AdminApplicationItem(UUID applicationId, UUID schoolId, String schoolName,
            UUID applicantUserId, String applicantName, String title, String descriptionSummary,
            String status, int applicationVersion, UUID createdActivityId,
            Instant reviewedAt, Instant createdAt, Instant updatedAt) {}

    record AdminApplicationDetail(UUID applicationId, UUID schoolId, String schoolName,
            UUID applicantUserId, String applicantName, String title, String description,
            String status, int applicationVersion, UUID createdActivityId,
            Instant reviewedAt, String reviewComment, String rejectReason,
            Instant createdAt, Instant updatedAt) {}

    record ApplicationStats(int total, int draft, int submitted, int approved, int rejected, int withdrawn, int createdToday) {}
    ApplicationStats getStats();

    record SchoolOption(UUID schoolId, String schoolName) {}
    List<SchoolOption> getApplicationSchools();
}
