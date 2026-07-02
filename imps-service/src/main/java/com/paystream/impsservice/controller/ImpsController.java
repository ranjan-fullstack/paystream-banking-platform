package com.paystream.impsservice.controller;

import com.paystream.impsservice.dto.*;
import com.paystream.impsservice.service.ImpsService;
import com.paystream.impsservice.service.MmidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/imps")
@RequiredArgsConstructor
public class ImpsController {

    private final ImpsService impsService;
    private final MmidService mmidService;

    @PostMapping("/transfer")
    public ResponseEntity<ImpsTransactionResponse> transfer(@Valid @RequestBody ImpsTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(impsService.transfer(request));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<ImpsTransactionResponse> track(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(impsService.trackStatus(referenceNumber));
    }

    @GetMapping("/customer/{customerId}/history")
    public ResponseEntity<List<ImpsTransactionResponse>> history(@PathVariable String customerId) {
        return ResponseEntity.ok(impsService.getHistory(customerId));
    }

    @PostMapping("/mmid/register")
    public ResponseEntity<MmidResponse> registerMmid(@Valid @RequestBody MmidRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mmidService.register(request));
    }

    @GetMapping("/mmid/{mobile}")
    public ResponseEntity<MmidResponse> getMmid(@PathVariable String mobile) {
        return ResponseEntity.ok(mmidService.getByMobile(mobile));
    }
}
