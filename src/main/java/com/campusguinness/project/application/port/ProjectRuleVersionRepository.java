package com.campusguinness.project.application.port;

import java.util.List;
import java.util.UUID;

public interface ProjectRuleVersionRepository {

    ProjectRuleVersionSnapshot save(ProjectRuleVersionSnapshot snapshot);

    int nextVersionNumber(UUID projectId);

    List<ProjectRuleVersionSnapshot> findAllByProjectId(UUID projectId);
}
