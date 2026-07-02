package com.paystream.upiservice.controller;

import com.paystream.upiservice.dto.*;
import com.paystream.upiservice.service.UpiTransactionService;
import com.paystream.upiservice.service.VpaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/upi")
@RequiredArgsConstructor
public class UpiController {

    private final VpaService vpaService;
    private final UpiTransactionService upiTransactionService;

    @PostMapping("/vpa/register")
    public ResponseEntity<VpaResponse> registerVpa(@Valid @RequestBody VpaRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vpaService.register(request));
    }

    @GetMapping("/vpa/{vpa}")
    public ResponseEntity<VpaResponse> getVpa(@PathVariable String vpa) {
        return ResponseEntity.ok(vpaService.getByVpa(vpa));
    }

    @GetMapping("/vpa/customer/{customerId}")
    public ResponseEntity<List<VpaResponse>> getVpasByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(vpaService.getByCustomerId(customerId));
    }

    @PostMapping("/vpa/set-pin")
    public ResponseEntity<Void> setPin(@Valid @RequestBody SetPinRequest request) {
        vpaService.setPin(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pay")
    public ResponseEntity<UpiTransactionResponse> pay(@Valid @RequestBody UpiPayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(upiTransactionService.pay(request));
    }

    @PostMapping("/collect")
    public ResponseEntity<UpiTransactionResponse> collect(@Valid @RequestBody UpiCollectMoneyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(upiTransactionService.collect(request));
    }

    @PutMapping("/collect/{txnId}/respond")
    public ResponseEntity<UpiTransactionResponse> respondToCollect(@PathVariable String txnId,
                                                                      @Valid @RequestBody CollectRespondRequest request) {
        return ResponseEntity.ok(upiTransactionService.respondToCollect(txnId, request));
    }

    @PostMapping("/refund")
    public ResponseEntity<UpiTransactionResponse> refund(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(upiTransactionService.refund(request));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<UpiTransactionResponse> getStatus(@PathVariable String transactionId) {
        return ResponseEntity.ok(upiTransactionService.getStatus(transactionId));
    }

    @GetMapping("/customer/{vpa}/history")
    public ResponseEntity<List<UpiTransactionResponse>> history(@PathVariable String vpa) {
        return ResponseEntity.ok(upiTransactionService.getHistory(vpa));
    }
}
