package com.campusguinness.score.application.port;

import java.util.UUID;

public interface ScoreCorrectionRecordPort {
    void append(UUID originalScoreId, UUID newScoreId, String reason, UUID correctedBy);
}
