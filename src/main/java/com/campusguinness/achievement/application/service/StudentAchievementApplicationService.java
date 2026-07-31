package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StudentAchievementApplicationService {

    private final AchievementRecordQueryPort records;

    public StudentAchievementApplicationService(
            AchievementRecordQueryPort records) {
        this.records = records;
    }

    public QueryPage<AchievementRecordItem> list(
            UUID actorId,
            String status,
            String keyword,
            int page,
            int size) {
        SchoolAdminAchievementApplicationService.validatePage(page, size);
        return records.findStudentRecords(
                actorId,
                SchoolAdminAchievementApplicationService.normalizeStatus(status),
                SchoolAdminAchievementApplicationService.normalizeKeyword(keyword),
                page,
                size);
    }

    public AchievementRecordDetail get(UUID actorId, UUID recordId) {
        return records.findStudentRecord(actorId, recordId)
                .orElseThrow(AchievementNotFoundException::new);
    }
}
