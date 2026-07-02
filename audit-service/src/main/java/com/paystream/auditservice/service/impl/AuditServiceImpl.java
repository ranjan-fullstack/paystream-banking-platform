package com.paystream.auditservice.service.impl;

import com.paystream.auditservice.dto.AuditLogResponse;
import com.paystream.auditservice.entity.AuditLog;
import com.paystream.auditservice.repository.AuditLogRepository;
import com.paystream.auditservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void record(String eventType, String entityType, String entityId, String performedBy, String newValue) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setNewValue(newValue);
        auditLogRepository.save(log);
    }

    @Override
    public List<AuditLogResponse> getByEntityId(String entityId) {
        return auditLogRepository.findByEntityIdOrderByTimestampDesc(entityId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getByPerformedBy(String performedBy) {
        return auditLogRepository.findByPerformedByOrderByTimestampDesc(performedBy)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<AuditLogResponse> getByEventType(String eventType) {
        return auditLogRepository.findByEventTypeOrderByTimestampDesc(eventType)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .performedBy(log.getPerformedBy())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .timestamp(log.getTimestamp())
                .build();
    }
}
