package com.campusguinness.appeal.application.port;
import com.campusguinness.appeal.internal.domain.ScoreAppeal;
import com.campusguinness.appeal.internal.domain.ScoreAppealId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ScoreAppealRepository { void save(ScoreAppeal a); Optional<ScoreAppeal> findById(ScoreAppealId id); List<ScoreAppeal> findByStudentId(UUID studentId); Optional<ScoreAppeal> findByIdAndStudentId(UUID id, UUID studentId); }
