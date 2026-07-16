package com.campusguinness.project.application.service;

import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.exception.ChallengeProjectNotFoundException;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.internal.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for ChallengeProject use cases.
 * Depends on domain port — no JPA Entity or Spring Data dependency.
 */
@Service
@Transactional
public class ChallengeProjectApplicationService {

    private final ChallengeProjectRepository repository;

    public ChallengeProjectApplicationService(ChallengeProjectRepository repository) {
        this.repository = repository;
    }

    /** Create a new ChallengeProject in DRAFT status. */
    public ChallengeProjectResult create(CreateChallengeProjectCommand cmd) {
        ChallengeProjectId id = new ChallengeProjectId(UUID.randomUUID());
        ProjectName name = new ProjectName(cmd.name());
        ProjectCategory category = new ProjectCategory(cmd.category());
        ScoreConfig scoreConfig = new ScoreConfig(
                ScoreStorageType.valueOf(cmd.scoreStorageType()),
                ScoreIndicatorType.valueOf(cmd.scoreIndicatorType()),
                ComparisonDirection.valueOf(cmd.comparisonDirection()),
                cmd.scoreUnit(), cmd.decimalPlaces(), cmd.effectiveScoreRule(),
                cmd.gradeOrder(), cmd.rulesText(), cmd.allowTie());

        ChallengeProject project = ChallengeProject.create(id, name, category, scoreConfig, cmd.description());
        repository.save(project);

        return new ChallengeProjectResult(id.value(), cmd.name(), project.status().name());
    }

    /** Find by ID — returns domain aggregate (not entity). */
    @Transactional(readOnly = true)
    public ChallengeProject findById(UUID id) {
        return repository.findById(new ChallengeProjectId(id))
                .orElseThrow(() -> new ChallengeProjectNotFoundException(
                        "ChallengeProject not found: " + id));
    }

    /** Publish: DRAFT → PUBLISHED. */
    public ChallengeProjectResult publish(UUID id) {
        ChallengeProject project = findById(id);
        project.publish();
        repository.save(project);
        return new ChallengeProjectResult(id, project.name().value(), project.status().name());
    }
}
