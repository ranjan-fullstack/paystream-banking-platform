package com.paystream.accountservice.integration;

import com.paystream.accountservice.dto.AmountRequest;
import com.paystream.accountservice.dto.BalanceResponse;
import com.paystream.accountservice.dto.OpenAccountRequest;
import com.paystream.accountservice.entity.OutboxEvent;
import com.paystream.accountservice.enums.AccountType;
import com.paystream.accountservice.exception.InsufficientBalanceException;
import com.paystream.accountservice.outbox.OutboxPublisher;
import com.paystream.accountservice.repository.OutboxEventRepository;
import com.paystream.accountservice.service.AccountService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.cloud.bus.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "management.tracing.enabled=false",
        "spring.cloud.config.import-check.enabled=false"
    }
)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@DisplayName("Pessimistic locking and outbox exactly-once integration tests")
class PessimisticLockingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("paystream_accounts")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("Concurrent debits: exactly one succeeds when only one has sufficient balance")
    void testConcurrentDebits_pessimisticLock_preventsDoubleSpend() throws Exception {
        // Open a SAVINGS account (starts with balance 0)
        OpenAccountRequest req = new OpenAccountRequest();
        req.setCustomerId("CIF-LOCK-TEST-" + UUID.randomUUID());
        req.setAccountType(AccountType.SAVINGS);
        req.setBranchCode("TEST01");
        var account = accountService.openAccount(req);

        // Credit 1000 to give the account a balance to debit from
        AmountRequest creditReq = new AmountRequest();
        creditReq.setAmount(new BigDecimal("1000.00"));
        creditReq.setReferenceNumber("SETUP-CREDIT-001");
        accountService.credit(account.getAccountNumber(), creditReq);

        // Both threads will try to debit 900 — only one should succeed
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    AmountRequest debitReq = new AmountRequest();
                    debitReq.setAmount(new BigDecimal("900.00"));
                    debitReq.setReferenceNumber("DEBIT-THREAD-" + threadNum);
                    accountService.debit(account.getAccountNumber(), debitReq);
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(successCount.get())
                .as("Exactly one debit should succeed")
                .isEqualTo(1);
        assertThat(failCount.get())
                .as("Exactly one debit should fail with InsufficientBalanceException")
                .isEqualTo(1);

        // Final balance must be exactly 100.00 — pessimistic lock prevented double-spend
        BalanceResponse balance = accountService.getBalance(account.getId());
        assertThat(balance.getBalance())
                .as("Final balance must be 100.00, not negative (no double-spend)")
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Debit with insufficient balance throws InsufficientBalanceException")
    void testDebit_insufficientBalance_throwsException() {
        OpenAccountRequest req = new OpenAccountRequest();
        req.setCustomerId("CIF-INSUFFICIENT-TEST-" + UUID.randomUUID());
        req.setAccountType(AccountType.SAVINGS);
        req.setBranchCode("TEST01");
        var account = accountService.openAccount(req);

        AmountRequest debitReq = new AmountRequest();
        debitReq.setAmount(new BigDecimal("500.00"));
        debitReq.setReferenceNumber("DEBIT-INSUFFICIENT-001");

        assertThatThrownBy(() -> accountService.debit(account.getAccountNumber(), debitReq))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("Insufficient available balance on account: " + account.getAccountNumber());
    }

    @Test
    @DisplayName("OutboxPublisher publishes exactly once per event — idempotent on second call")
    void testOutboxPublisher_publishesExactlyOnce() throws Exception {
        String testTopic = "test.outbox.exactly-once";

        // Insert an unpublished outbox event
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("TestAggregate");
        event.setAggregateId("TEST-AGG-" + UUID.randomUUID());
        event.setEventType("TestEvent");
        event.setTopic(testTopic);
        event.setPayload("{\"test\":\"exactly-once-payload\"}");
        outboxEventRepository.saveAndFlush(event);

        // First call: publishes the event
        outboxPublisher.publishPendingEvents();

        // Second call: finds nothing to publish (event is already marked published)
        outboxPublisher.publishPendingEvents();

        // Verify the event is marked as published in the DB
        OutboxEvent saved = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.isPublished()).isTrue();
        assertThat(saved.getPublishedAt()).isNotNull();

        // Verify Kafka received exactly 1 message
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(),
                "test-consumer-" + UUID.randomUUID(),
                "false"));
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(Collections.singletonList(testTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            assertThat(records.count())
                    .as("Kafka should contain exactly 1 message despite publisher being called twice")
                    .isEqualTo(1);
            assertThat(records.iterator().next().value())
                    .isEqualTo("{\"test\":\"exactly-once-payload\"}");
        }
    }
}
