package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.query.model.TeacherProjectParticipantItem;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectDetail;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectItem;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface TeacherResponsibleProjectQueryPort {
    QueryPage<TeacherResponsibleProjectItem> findResponsibleProjects(
            UUID actorId,
            String executionStatus,
            String keyword,
            int page,
            int size);

    Optional<TeacherResponsibleProjectDetail> findResponsibleProject(
            UUID actorId,
            UUID activityProjectId);

    QueryPage<TeacherProjectParticipantItem> findProjectParticipants(
            UUID actorId,
            UUID activityProjectId,
            String keyword,
            String status,
            int page,
            int size);
}
