package com.paystream.auditservice.controller;

import com.paystream.auditservice.dto.AuditLogResponse;
import com.paystream.auditservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "COMPLIANCE_OFFICER");

    private final AuditService auditService;

    @GetMapping("/entity/{entityId}")
    public ResponseEntity<List<AuditLogResponse>> getByEntity(@PathVariable String entityId) {
        return ResponseEntity.ok(auditService.getByEntityId(entityId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AuditLogResponse>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(auditService.getByPerformedBy(userId));
    }

    @GetMapping("/type/{eventType}")
    public ResponseEntity<List<AuditLogResponse>> getByType(@PathVariable String eventType,
                                                              @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(auditService.getByEventType(eventType));
    }
}
