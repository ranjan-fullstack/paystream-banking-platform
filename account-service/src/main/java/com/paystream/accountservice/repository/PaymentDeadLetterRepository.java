package com.paystream.accountservice.repository;

import com.paystream.accountservice.entity.PaymentDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentDeadLetterRepository extends JpaRepository<PaymentDeadLetter, UUID> {
}
