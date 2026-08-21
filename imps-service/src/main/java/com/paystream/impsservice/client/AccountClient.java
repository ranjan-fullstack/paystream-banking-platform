package com.paystream.impsservice.client;

import com.paystream.impsservice.client.dto.AccountValidationResponse;
import com.paystream.impsservice.client.dto.AmountRequest;
import com.paystream.impsservice.client.dto.PaymentRailConfigResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-service", path = "/internal/accounts", fallback = AccountClientFallback.class)
public interface AccountClient {

    @GetMapping("/{accountNumber}/validate")
    AccountValidationResponse validate(@PathVariable("accountNumber") String accountNumber);

    @PutMapping("/{accountNumber}/debit")
    void debit(@PathVariable("accountNumber") String accountNumber, @RequestBody AmountRequest request);

    @PutMapping("/{accountNumber}/credit")
    void credit(@PathVariable("accountNumber") String accountNumber, @RequestBody AmountRequest request);

    @PutMapping("/{accountNumber}/reverseDebit")
    void reverseDebit(@PathVariable("accountNumber") String accountNumber, @RequestBody AmountRequest request);

    @GetMapping("/{accountNumber}/payment-config/{paymentMode}")
    PaymentRailConfigResponse getPaymentConfig(@PathVariable("accountNumber") String accountNumber, @PathVariable("paymentMode") String paymentMode);
}