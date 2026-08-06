package com.campusguinness.audit.internal.persistence;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuditRecordCommandAdapter implements AuditRecordCommandPort {

    private final AuditRecordJpaRepository auditRecords;

    AuditRecordCommandAdapter(AuditRecordJpaRepository auditRecords) {
        this.auditRecords = auditRecords;
    }

    @Override
    @Transactional
    public void record(AuditRecordCommand command) {
        var entity = new AuditRecordEntity();
        entity.setId(command.id());
        entity.setSchoolId(command.schoolId());
        entity.setActorId(command.actorId());
        entity.setAction(command.action());
        entity.setTargetType(command.targetType());
        entity.setTargetId(command.targetId());
        entity.setDetail(command.detail());
        entity.setCreatedAt(command.occurredAt());
        auditRecords.save(entity);
    }
}
