package com.paystream.transactionservice.controller;

import com.paystream.transactionservice.dto.TransactionResponse;
import com.paystream.transactionservice.dto.TransactionSummaryResponse;
import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getByTransactionId(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.getByTransactionId(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getByAccount(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) PaymentMode mode) {
        return ResponseEntity.ok(transactionService.getByAccount(accountNumber, from, to, mode));
    }

    @GetMapping("/statement/{accountNumber}")
    public ResponseEntity<byte[]> getStatement(@PathVariable String accountNumber) {
        byte[] pdf = transactionService.generateStatement(accountNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-" + accountNumber + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/summary/{accountNumber}")
    public ResponseEntity<TransactionSummaryResponse> getSummary(@PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getSummary(accountNumber));
    }
}
