package com.paystream.frauddetectionservice.repository;

import com.paystream.frauddetectionservice.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
}
