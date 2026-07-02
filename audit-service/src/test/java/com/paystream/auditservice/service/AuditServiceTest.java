package com.paystream.auditservice.service;

import com.paystream.auditservice.dto.AuditLogResponse;
import com.paystream.auditservice.entity.AuditLog;
import com.paystream.auditservice.repository.AuditLogRepository;
import com.paystream.auditservice.service.impl.AuditServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService unit tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    private AuditLog buildLog(String eventType, String entityType, String entityId, String performedBy) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPerformedBy(performedBy);
        log.setNewValue("{\"status\":\"COMPLETED\"}");
        log.setTimestamp(LocalDateTime.now());
        return log;
    }

    @Test
    @DisplayName("Should persist a new audit log entry successfully")
    void testCreateAuditLog_Success() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        auditService.record("NEFT_COMPLETED", "TRANSACTION", "NEFT202606290001", "system", "{\"status\":\"COMPLETED\"}");

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("NEFT_COMPLETED");
        assertThat(captor.getValue().getEntityType()).isEqualTo("TRANSACTION");
        assertThat(captor.getValue().getEntityId()).isEqualTo("NEFT202606290001");
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("Should fetch audit logs for a given entity ID ordered by most recent")
    void testGetAuditByEntityId_Success() {
        // Given
        AuditLog log = buildLog("ACCOUNT_FROZEN", "ACCOUNT", "1111222233334444", "compliance.officer");
        when(auditLogRepository.findByEntityIdOrderByTimestampDesc("1111222233334444")).thenReturn(List.of(log));

        // When
        List<AuditLogResponse> result = auditService.getByEntityId("1111222233334444");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityId()).isEqualTo("1111222233334444");
        assertThat(result.get(0).getEventType()).isEqualTo("ACCOUNT_FROZEN");
    }

    @Test
    @DisplayName("Should fetch audit logs filtered by the user who performed the action")
    void testGetAuditByUserId_Success() {
        // Given
        AuditLog log = buildLog("FRAUD_ALERT_REVIEWED", "FRAUD_ALERT", "alert-123", "fraud.analyst");
        when(auditLogRepository.findByPerformedByOrderByTimestampDesc("fraud.analyst")).thenReturn(List.of(log));

        // When
        List<AuditLogResponse> result = auditService.getByPerformedBy("fraud.analyst");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPerformedBy()).isEqualTo("fraud.analyst");
    }

    @Test
    @DisplayName("Should fetch audit logs filtered by event type")
    void testGetAuditByEventType_Success() {
        // Given
        AuditLog log1 = buildLog("NEFT_COMPLETED", "TRANSACTION", "NEFT202606290001", "system");
        AuditLog log2 = buildLog("NEFT_COMPLETED", "TRANSACTION", "NEFT202606290002", "system");
        when(auditLogRepository.findByEventTypeOrderByTimestampDesc("NEFT_COMPLETED")).thenReturn(List.of(log2, log1));

        // When
        List<AuditLogResponse> result = auditService.getByEventType("NEFT_COMPLETED");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getEventType().equals("NEFT_COMPLETED"));
    }

    @Test
    @DisplayName("Should not expose any update operation on the audit log contract, since audit logs are append-only")
    void testAuditLog_isImmutable_cannotUpdate() {
        // Given / When
        boolean hasUpdateMethod = methodNameContains("update");

        // Then
        assertThat(hasUpdateMethod).isFalse();
    }

    @Test
    @DisplayName("Should not expose any delete operation on the audit log contract, since audit logs are append-only")
    void testAuditLog_isImmutable_cannotDelete() {
        // Given / When
        boolean hasDeleteMethod = methodNameContains("delete") || methodNameContains("remove");

        // Then
        assertThat(hasDeleteMethod).isFalse();
    }

    private boolean methodNameContains(String keyword) {
        for (Method method : AuditService.class.getMethods()) {
            if (method.getName().toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
