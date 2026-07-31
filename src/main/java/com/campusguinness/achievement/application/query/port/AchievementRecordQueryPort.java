package com.campusguinness.achievement.application.query.port;

import com.campusguinness.achievement.application.query.model.AchievementRecordDetail;
import com.campusguinness.achievement.application.query.model.AchievementRecordItem;
import com.campusguinness.achievement.application.query.model.PublicAchievementVerification;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementItem;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementStatus;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AchievementRecordQueryPort {

    QueryPage<AchievementRecordItem> findStudentRecords(
            UUID studentId, String status, String keyword, int page, int size);

    Optional<AchievementRecordDetail> findStudentRecord(
            UUID studentId, UUID recordId);

    Optional<PublicAchievementVerification> findPublicVerification(
            String verificationCode);

    boolean existsSchoolProject(UUID schoolId, UUID activityProjectId);

    boolean existsSchoolL1Version(UUID schoolId, UUID rankingVersionId);

    QueryPage<SchoolAdminAchievementItem> findSchoolProjectRecords(
            UUID schoolId,
            UUID activityProjectId,
            String status,
            String keyword,
            int page,
            int size);

    Optional<SchoolAdminAchievementDetail> findSchoolRecord(
            UUID schoolId, UUID recordId);

    List<SchoolAdminAchievementStatus> findVersionStatuses(
            UUID schoolId, UUID rankingVersionId);

    List<SchoolAdminAchievementDetail> findProjectRecords(
            UUID activityProjectId);
}
