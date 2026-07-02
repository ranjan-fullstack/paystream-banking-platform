package com.paystream.auditservice.service;

import com.paystream.auditservice.dto.AuditLogResponse;

import java.util.List;

public interface AuditService {
    void record(String eventType, String entityType, String entityId, String performedBy, String newValue);
    List<AuditLogResponse> getByEntityId(String entityId);
    List<AuditLogResponse> getByPerformedBy(String performedBy);
    List<AuditLogResponse> getByEventType(String eventType);
}
