package com.campusguinness.project.application.port;

import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import java.util.Optional;

/** Domain repository port — no JPA dependency. */
public interface ChallengeProjectRepository {
    void save(ChallengeProject project);
    Optional<ChallengeProject> findById(ChallengeProjectId id);
    void flush();
}
