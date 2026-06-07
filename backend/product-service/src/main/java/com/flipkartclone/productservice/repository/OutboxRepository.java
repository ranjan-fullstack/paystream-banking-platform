package com.flipkartclone.productservice.repository;

import com.flipkartclone.productservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(String status);
}
