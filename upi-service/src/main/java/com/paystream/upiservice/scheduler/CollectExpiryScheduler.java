package com.paystream.upiservice.scheduler;

import com.paystream.upiservice.entity.UpiCollectRequest;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.enums.CollectRequestStatus;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.repository.UpiCollectRequestRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UPI collect requests expire 30 minutes after creation if the payer never responds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectExpiryScheduler {

    private final UpiTransactionRepository upiTransactionRepository;
    private final UpiCollectRequestRepository upiCollectRequestRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireStaleCollectRequests() {
        List<UpiTransaction> expired = upiTransactionRepository
                .findByStatusAndExpiresAtBefore(UpiTransactionStatus.PENDING_PIN, LocalDateTime.now());

        for (UpiTransaction txn : expired) {
            txn.setStatus(UpiTransactionStatus.EXPIRED);
            upiCollectRequestRepository.findByUpiTransactionId(txn.getId()).ifPresent(req -> {
                req.setStatus(CollectRequestStatus.EXPIRED);
                upiCollectRequestRepository.save(req);
            });
        }
        if (!expired.isEmpty()) {
            upiTransactionRepository.saveAll(expired);
            log.info("Expired {} stale UPI collect request(s)", expired.size());
        }
    }
}
