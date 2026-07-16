package com.campusguinness.appeal.application.port;
import com.campusguinness.appeal.internal.domain.ScoreAppeal;
import com.campusguinness.appeal.internal.domain.ScoreAppealId;
import java.util.Optional;
public interface ScoreAppealRepository { void save(ScoreAppeal a); Optional<ScoreAppeal> findById(ScoreAppealId id); }
