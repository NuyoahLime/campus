package com.campusguinness.audit.application.port;

public interface AuditRecordCommandPort {

    void record(AuditRecordCommand command);
}
