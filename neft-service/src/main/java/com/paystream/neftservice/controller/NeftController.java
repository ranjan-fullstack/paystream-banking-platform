package com.paystream.neftservice.controller;

import com.paystream.neftservice.dto.NeftBatchResponse;
import com.paystream.neftservice.dto.NeftTransactionResponse;
import com.paystream.neftservice.dto.NeftTransferRequest;
import com.paystream.neftservice.service.NeftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/neft")
@RequiredArgsConstructor
public class NeftController {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    private final NeftService neftService;

    @PostMapping("/transfer")
    public ResponseEntity<NeftTransactionResponse> transfer(@Valid @RequestBody NeftTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(neftService.initiateTransfer(request));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<NeftTransactionResponse> track(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(neftService.trackStatus(referenceNumber));
    }

    @GetMapping("/customer/{customerId}/history")
    public ResponseEntity<List<NeftTransactionResponse>> history(@PathVariable String customerId) {
        return ResponseEntity.ok(neftService.getHistory(customerId));
    }

    @GetMapping("/batches")
    public ResponseEntity<List<NeftBatchResponse>> batches(@RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(neftService.getBatches());
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<NeftBatchResponse> batchDetails(@PathVariable String batchId,
                                                            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(neftService.getBatchDetails(batchId));
    }
}
