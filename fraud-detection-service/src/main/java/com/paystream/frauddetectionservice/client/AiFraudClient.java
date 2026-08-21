package com.paystream.frauddetectionservice.client;

import com.paystream.frauddetectionservice.client.dto.AiFraudResponse;
import com.paystream.frauddetectionservice.client.dto.FraudAnalysisRequest;
import com.paystream.frauddetectionservice.entity.TransactionLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls ai-service's LLM+RAG fraud-analysis endpoint directly by URL — ai-service
 * is a FastAPI app, not a Eureka client, so this is a plain WebClient call rather
 * than a Feign client. Any failure (timeout, connection refused, 5xx) is treated
 * as graceful degradation: log a warning and return null so the rule-engine-based
 * pipeline keeps working even when ai-service is unavailable.
 */
@Component
@Slf4j
public class AiFraudClient {

    private final WebClient webClient;

    public AiFraudClient(WebClient.Builder builder, @Value("${ai-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public AiFraudResponse analyze(TransactionLog txn) {
        FraudAnalysisRequest request = FraudAnalysisRequest.builder()
                .transactionReference(txn.getPaymentReferenceNumber())
                .accountNumber(txn.getAccountNumber())
                .amount(txn.getAmount())
                .paymentMode(txn.getPaymentMode())
                .beneficiaryAccount(txn.getBeneficiaryAccountNumber())
                .transactionTime(txn.getOccurredAt().toString())
                .recentTransactions(List.of())
                .customerRiskRating("MEDIUM")
                .build();

        try {
            return webClient.post()
                    .uri("/api/v1/ai/fraud/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiFraudResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("ai-service fraud analysis unavailable for transaction {}: {}",
                    txn.getPaymentReferenceNumber(), e.getMessage());
            return null;
        }
    }
}
