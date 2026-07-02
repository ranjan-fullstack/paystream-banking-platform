package com.paystream.auditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuditLogResponse {
    private UUID id;
    private String eventType;
    private String entityType;
    private String entityId;
    private String performedBy;
    private String oldValue;
    private String newValue;
    private LocalDateTime timestamp;
}
