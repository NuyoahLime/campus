package com.campusguinness.achievement.application.service;

import com.campusguinness.achievement.application.exception.AchievementNotFoundException;
import com.campusguinness.achievement.application.query.model.PublicAchievementVerification;
import com.campusguinness.achievement.application.query.port.AchievementRecordQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class PublicAchievementVerificationService {

    private static final Pattern CODE = Pattern.compile("^[0-9a-f]{32}$");

    private final AchievementRecordQueryPort records;

    public PublicAchievementVerificationService(
            AchievementRecordQueryPort records) {
        this.records = records;
    }

    public PublicAchievementVerification verify(String verificationCode) {
        String normalized = verificationCode == null
                ? ""
                : verificationCode.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new AchievementNotFoundException();
        }
        return records.findPublicVerification(normalized)
                .orElseThrow(AchievementNotFoundException::new);
    }
}
