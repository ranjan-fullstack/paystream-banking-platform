package com.paystream.transactionservice.repository;

import com.paystream.transactionservice.entity.Transaction;
import com.paystream.transactionservice.enums.PaymentMode;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> forAccount(String accountNumber, LocalDateTime from, LocalDateTime to, PaymentMode mode) {
        return (root, query, cb) -> {
            Predicate accountPredicate = cb.or(
                    cb.equal(root.get("debitAccountNumber"), accountNumber),
                    cb.equal(root.get("creditAccountNumber"), accountNumber)
            );
            Predicate predicate = cb.and(accountPredicate);

            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("initiatedAt"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("initiatedAt"), to));
            }
            if (mode != null) {
                predicate = cb.and(predicate, cb.equal(root.get("paymentMode"), mode));
            }
            return predicate;
        };
    }
}
