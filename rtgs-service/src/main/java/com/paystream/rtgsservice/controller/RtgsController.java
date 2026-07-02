package com.paystream.rtgsservice.controller;

import com.paystream.rtgsservice.dto.RtgsTransactionResponse;
import com.paystream.rtgsservice.dto.RtgsTransferRequest;
import com.paystream.rtgsservice.service.RtgsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/rtgs")
@RequiredArgsConstructor
public class RtgsController {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    private final RtgsService rtgsService;

    @PostMapping("/transfer")
    public ResponseEntity<RtgsTransactionResponse> transfer(@Valid @RequestBody RtgsTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rtgsService.initiateTransfer(request));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<RtgsTransactionResponse> track(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(rtgsService.trackStatus(referenceNumber));
    }

    @GetMapping("/customer/{customerId}/history")
    public ResponseEntity<List<RtgsTransactionResponse>> history(@PathVariable String customerId) {
        return ResponseEntity.ok(rtgsService.getHistory(customerId));
    }

    @PostMapping("/{referenceNumber}/recall")
    public ResponseEntity<RtgsTransactionResponse> recall(@PathVariable String referenceNumber,
                                                            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(rtgsService.recall(referenceNumber));
    }
}
