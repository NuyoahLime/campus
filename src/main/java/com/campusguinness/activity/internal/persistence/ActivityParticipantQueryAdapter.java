package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
class ActivityParticipantQueryAdapter implements ActivityParticipantQueryPort {

    private final ActivityParticipantJpaRepository participantJpa;
    private final ActivityProjectParticipantJpaRepository projectParticipantJpa;

    ActivityParticipantQueryAdapter(ActivityParticipantJpaRepository participantJpa,
                                     ActivityProjectParticipantJpaRepository projectParticipantJpa) {
        this.participantJpa = participantJpa;
        this.projectParticipantJpa = projectParticipantJpa;
    }

    @Override
    public QueryPage<ParticipantListResult> findByActivity(UUID activityId, String keyword, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var result = participantJpa.findByActivityId(activityId, pageable);

        var participantIds = result.getContent().stream()
                .map(ActivityParticipantEntity::getId).toList();
        var projectCounts = projectParticipantJpa.findByActivityParticipantIdIn(participantIds)
                .stream().collect(Collectors.groupingBy(
                        ActivityProjectParticipantEntity::getActivityParticipantId, Collectors.counting()));

        var items = result.getContent().stream().map(e -> {
            long count = projectCounts.getOrDefault(e.getId(), 0L);
            return new ParticipantListResult(e.getId(), e.getActivityId(), e.getStudentMembershipId(),
                    null, null, null, null, null, count, false, e.getCreatedAt());
        }).toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public List<UUID> findParticipantIdsByMembership(UUID studentMembershipId) {
        return participantJpa.findByStudentMembershipId(studentMembershipId)
                .stream().map(ActivityParticipantEntity::getId).toList();
    }

    @Override
    public List<UUID> findParticipantIdsByMembershipIds(List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return List.of();
        return participantJpa.findByStudentMembershipIdIn(membershipIds)
                .stream().map(ActivityParticipantEntity::getId).toList();
    }

    @Override
    public Optional<ParticipantListResult> findByActivityAndMemberships(UUID activityId, List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return Optional.empty();
        var entities = participantJpa.findByStudentMembershipIdIn(membershipIds);
        return entities.stream()
                .filter(e -> e.getActivityId().equals(activityId))
                .findFirst()
                .map(e -> new ParticipantListResult(e.getId(), e.getActivityId(), e.getStudentMembershipId(),
                        null, null, null, null, null, 0, false, e.getCreatedAt()));
    }
}
