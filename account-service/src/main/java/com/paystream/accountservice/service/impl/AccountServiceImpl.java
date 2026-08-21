package com.paystream.accountservice.service.impl;

import com.paystream.accountservice.dto.*;
import com.paystream.accountservice.entity.AccountLimit;
import com.paystream.accountservice.entity.AccountPaymentConfig;
import com.paystream.accountservice.entity.BankAccount;
import com.paystream.accountservice.enums.AccountStatus;
import com.paystream.accountservice.enums.PaymentMode;
import com.paystream.accountservice.exception.AccountNotActiveException;
import com.paystream.accountservice.exception.AccountNotFoundException;
import com.paystream.accountservice.exception.DailyLimitExceededException;
import com.paystream.accountservice.exception.InsufficientBalanceException;
import com.paystream.accountservice.repository.AccountLimitRepository;
import com.paystream.accountservice.repository.AccountPaymentConfigRepository;
import com.paystream.accountservice.repository.BankAccountRepository;
import com.paystream.accountservice.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.AccountCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ACCOUNT_NUMBER_RETRIES = 5;
    private static final String ACCOUNT_CREATED_TOPIC = "account.created";

    private final BankAccountRepository bankAccountRepository;
    private final AccountLimitRepository accountLimitRepository;
    private final AccountPaymentConfigRepository accountPaymentConfigRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        BankAccount account = new BankAccount();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setIfscCode("PAYS0" + request.getBranchCode());
        account.setAccountType(request.getAccountType());
        account.setCustomerId(request.getCustomerId());
        account.setUserId(request.getUserId());
        account.setBranchCode(request.getBranchCode());
        account.setNomineeName(request.getNomineeName());
        account.setStatus(AccountStatus.ACTIVE);
        account = bankAccountRepository.save(account);

        seedDefaultLimits(account);
        publishAccountCreated(account);

        return toResponse(account);
    }

    private void publishAccountCreated(BankAccount account) {
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountNumber(account.getAccountNumber())
                .customerId(account.getCustomerId())
                .accountType(account.getAccountType().name())
                .createdAt(Instant.now())
                .build();
        try {
            kafkaTemplate.send(ACCOUNT_CREATED_TOPIC, account.getAccountNumber(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish AccountCreatedEvent for account {}", account.getAccountNumber(), e);
        }
    }

    @Override
    public AccountResponse getById(UUID accountId) {
        return toResponse(findByIdOrThrow(accountId));
    }

    @Override
    public AccountResponse getByAccountNumber(String accountNumber) {
        return toResponse(findByAccountNumberOrThrow(accountNumber));
    }

    @Override
    public List<AccountResponse> getByCustomerId(String customerId) {
        return bankAccountRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AccountResponse> getByUserId(Long userId) {
        return bankAccountRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponse updateStatus(UUID accountId, AccountStatusUpdateRequest request) {
        BankAccount account = findByIdOrThrow(accountId);
        account.setStatus(request.getStatus());
        return toResponse(bankAccountRepository.save(account));
    }

    @Override
    public BalanceResponse getBalance(UUID accountId) {
        BankAccount account = findByIdOrThrow(accountId);
        return BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .holdAmount(account.getHoldAmount())
                .currency(account.getCurrency())
                .build();
    }

    @Override
    public List<AccountLimitResponse> getLimits(UUID accountId) {
        BankAccount account = findByIdOrThrow(accountId);
        return accountLimitRepository.findByAccountId(account.getId()).stream()
                .map(this::toLimitResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountLimitResponse updateLimit(UUID accountId, AccountLimitUpdateRequest request) {
        BankAccount account = findByIdOrThrow(accountId);
        AccountLimit limit = accountLimitRepository
                .findByAccountIdAndTransactionType(account.getId(), request.getTransactionType())
                .orElseGet(() -> {
                    AccountLimit newLimit = new AccountLimit();
                    newLimit.setAccount(account);
                    newLimit.setTransactionType(request.getTransactionType());
                    newLimit.setUsedTodayAmount(BigDecimal.ZERO);
                    newLimit.setResetAt(LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0));
                    return newLimit;
                });
        limit.setDailyLimit(request.getDailyLimit());
        limit.setPerTransactionLimit(request.getPerTransactionLimit());
        return toLimitResponse(accountLimitRepository.save(limit));
    }

    @Override
    public AccountValidationResponse validate(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber)
                .map(account -> AccountValidationResponse.builder()
                        .valid(account.getStatus() == AccountStatus.ACTIVE)
                        .accountNumber(account.getAccountNumber())
                        .status(account.getStatus())
                        .reason(account.getStatus() == AccountStatus.ACTIVE ? null : "Account is " + account.getStatus())
                        .build())
                .orElseGet(() -> AccountValidationResponse.builder()
                        .valid(false)
                        .accountNumber(accountNumber)
                        .status(null)
                        .reason("Account not found")
                        .build());
    }

    @Override
    @Transactional
    public void debit(String accountNumber, AmountRequest request) {
        BankAccount account = lockByAccountNumberOrThrow(accountNumber);
        requireActive(account);
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(accountNumber);
        }
        if (request.getPaymentMode() != null) {
            accountLimitRepository
                    .findByAccountIdAndTransactionType(account.getId(), request.getPaymentMode())
                    .ifPresent(limit -> {
                        if (limit.getUsedTodayAmount().add(request.getAmount())
                                .compareTo(limit.getDailyLimit()) > 0) {
                            throw new DailyLimitExceededException(
                                    request.getPaymentMode().name(), limit.getDailyLimit());
                        }
                        limit.setUsedTodayAmount(limit.getUsedTodayAmount().add(request.getAmount()));
                        accountLimitRepository.save(limit);
                    });
        }
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void credit(String accountNumber, AmountRequest request) {
        BankAccount account = lockByAccountNumberOrThrow(accountNumber);
        requireActive(account);
        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void hold(String accountNumber, AmountRequest request) {
        BankAccount account = lockByAccountNumberOrThrow(accountNumber);
        requireActive(account);
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(accountNumber);
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        account.setHoldAmount(account.getHoldAmount().add(request.getAmount()));
        bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void release(String accountNumber, AmountRequest request) {
        BankAccount account = lockByAccountNumberOrThrow(accountNumber);
        BigDecimal releaseAmount = request.getAmount().min(account.getHoldAmount());
        account.setHoldAmount(account.getHoldAmount().subtract(releaseAmount));
        account.setAvailableBalance(account.getAvailableBalance().add(releaseAmount));
        bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void reverseDebit(String accountNumber, AmountRequest request) {
        // Compensation: credit the sender back without limit tracking
        BankAccount account = lockByAccountNumberOrThrow(accountNumber);
        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        bankAccountRepository.save(account);
    }

    private void seedDefaultLimits(BankAccount account) {
        LocalDateTime nextReset = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);

        List<AccountLimit> limits = List.of(
                buildLimit(account, PaymentMode.NEFT, new BigDecimal("1000000"), new BigDecimal("1000000"), nextReset),
                buildLimit(account, PaymentMode.RTGS, new BigDecimal("10000000"), new BigDecimal("10000000"), nextReset),
                buildLimit(account, PaymentMode.IMPS, new BigDecimal("500000"), new BigDecimal("500000"), nextReset),
                buildLimit(account, PaymentMode.UPI, new BigDecimal("100000"), new BigDecimal("100000"), nextReset)
        );
        accountLimitRepository.saveAll(limits);
    }

    private AccountLimit buildLimit(BankAccount account, PaymentMode mode, BigDecimal daily, BigDecimal perTxn, LocalDateTime resetAt) {
        AccountLimit limit = new AccountLimit();
        limit.setAccount(account);
        limit.setTransactionType(mode);
        limit.setDailyLimit(daily);
        limit.setPerTransactionLimit(perTxn);
        limit.setUsedTodayAmount(BigDecimal.ZERO);
        limit.setResetAt(resetAt);
        return limit;
    }

    private void requireActive(BankAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getAccountNumber());
        }
    }

    private BankAccount findByIdOrThrow(UUID accountId) {
        return bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    private BankAccount findByAccountNumberOrThrow(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private BankAccount lockByAccountNumberOrThrow(String accountNumber) {
        return bankAccountRepository.findWithLockByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_RETRIES; attempt++) {
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 16; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            String candidate = sb.toString();
            if (!bankAccountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account number, please retry");
    }

    private AccountResponse toResponse(BankAccount account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ifscCode(account.getIfscCode())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .customerId(account.getCustomerId())
                .userId(account.getUserId())
                .branchCode(account.getBranchCode())
                .nomineeName(account.getNomineeName())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private AccountLimitResponse toLimitResponse(AccountLimit limit) {
        return AccountLimitResponse.builder()
                .id(limit.getId())
                .transactionType(limit.getTransactionType())
                .dailyLimit(limit.getDailyLimit())
                .perTransactionLimit(limit.getPerTransactionLimit())
                .usedTodayAmount(limit.getUsedTodayAmount())
                .build();
    }

    @Override
    @Transactional
    public AccountResponse openAccountByBranch(OpenAccountByBranchRequest request) {
        BankAccount account = new BankAccount();
        account.setAccountNumber(generateUniqueAccountNumber());
        // branchCode already carries the "PAYS" prefix (e.g. "PAYS0001") — do not
        // prepend it again, or the IFSC comes out as "PAYSPAYS0001001".
        account.setIfscCode(request.getBranchCode() + "001");
        account.setAccountType(request.getAccountType());
        account.setUserId(request.getUserId());
        // customerId is NOT NULL/immutable on BankAccount, but the branch-open flow
        // only has a userId, not a real CIF from customer-service — synthesize a
        // stable, obviously-derived placeholder rather than leaving it blank or
        // accepting arbitrary free text (which is how this column ended up holding
        // a username in production).
        account.setCustomerId("USR" + request.getUserId());
        account.setBranchCode(request.getBranchCode());
        account.setBalance(request.getInitialDeposit());
        account.setAvailableBalance(request.getInitialDeposit());
        account.setStatus(AccountStatus.ACTIVE);
        account = bankAccountRepository.save(account);

        seedDefaultLimits(account);

        AccountPaymentConfig config = new AccountPaymentConfig();
        config.setAccountId(account.getId());
        accountPaymentConfigRepository.save(config);

        publishAccountCreated(account);

        return toResponse(account);
    }

    @Override
    @Transactional
    public PaymentConfigResponse updatePaymentConfig(UUID accountId, PaymentConfigUpdateRequest request) {
        BankAccount account = findByIdOrThrow(accountId);
        AccountPaymentConfig config = accountPaymentConfigRepository.findByAccountId(account.getId())
                .orElseGet(() -> {
                    AccountPaymentConfig newConfig = new AccountPaymentConfig();
                    newConfig.setAccountId(account.getId());
                    return newConfig;
                });

        config.setNeftEnabled(request.getNeftEnabled());
        config.setRtgsEnabled(request.getRtgsEnabled());
        config.setImpsEnabled(request.getImpsEnabled());
        config.setUpiEnabled(request.getUpiEnabled());
        if (request.getNeftDailyLimit() != null) config.setNeftDailyLimit(request.getNeftDailyLimit());
        if (request.getNeftPerTransactionLimit() != null) config.setNeftPerTransactionLimit(request.getNeftPerTransactionLimit());
        if (request.getRtgsDailyLimit() != null) config.setRtgsDailyLimit(request.getRtgsDailyLimit());
        if (request.getRtgsPerTransactionLimit() != null) config.setRtgsPerTransactionLimit(request.getRtgsPerTransactionLimit());
        if (request.getImpsDailyLimit() != null) config.setImpsDailyLimit(request.getImpsDailyLimit());
        if (request.getImpsPerTransactionLimit() != null) config.setImpsPerTransactionLimit(request.getImpsPerTransactionLimit());
        if (request.getUpiDailyLimit() != null) config.setUpiDailyLimit(request.getUpiDailyLimit());
        if (request.getUpiPerTransactionLimit() != null) config.setUpiPerTransactionLimit(request.getUpiPerTransactionLimit());

        LocalDateTime now = LocalDateTime.now();
        if (config.getEnabledAt() == null) {
            config.setEnabledBy(request.getUpdatedBy());
            config.setEnabledAt(now);
        }
        config.setLastUpdatedBy(request.getUpdatedBy());
        config.setLastUpdatedAt(now);

        return toConfigResponse(accountPaymentConfigRepository.save(config));
    }

    @Override
    public List<AccountResponse> getAccountsByBranch(String branchCode) {
        return bankAccountRepository.findByBranchCode(branchCode).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentConfigResponse getPaymentConfig(UUID accountId) {
        BankAccount account = findByIdOrThrow(accountId);
        return accountPaymentConfigRepository.findByAccountId(account.getId())
                .map(this::toConfigResponse)
                .orElseGet(() -> defaultConfigResponse(account.getId()));
    }

    @Override
    @Transactional
    public AccountResponse freezeAccount(UUID accountId) {
        BankAccount account = findByIdOrThrow(accountId);
        account.setStatus(AccountStatus.FROZEN);
        return toResponse(bankAccountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountResponse unfreezeAccount(UUID accountId) {
        BankAccount account = findByIdOrThrow(accountId);
        account.setStatus(AccountStatus.ACTIVE);
        return toResponse(bankAccountRepository.save(account));
    }

    private PaymentConfigResponse toConfigResponse(AccountPaymentConfig config) {
        return PaymentConfigResponse.builder()
                .accountId(config.getAccountId())
                .neftEnabled(config.isNeftEnabled())
                .neftDailyLimit(config.getNeftDailyLimit())
                .neftPerTransactionLimit(config.getNeftPerTransactionLimit())
                .rtgsEnabled(config.isRtgsEnabled())
                .rtgsDailyLimit(config.getRtgsDailyLimit())
                .rtgsPerTransactionLimit(config.getRtgsPerTransactionLimit())
                .impsEnabled(config.isImpsEnabled())
                .impsDailyLimit(config.getImpsDailyLimit())
                .impsPerTransactionLimit(config.getImpsPerTransactionLimit())
                .upiEnabled(config.isUpiEnabled())
                .upiDailyLimit(config.getUpiDailyLimit())
                .upiPerTransactionLimit(config.getUpiPerTransactionLimit())
                .enabledBy(config.getEnabledBy())
                .enabledAt(config.getEnabledAt())
                .lastUpdatedBy(config.getLastUpdatedBy())
                .lastUpdatedAt(config.getLastUpdatedAt())
                .build();
    }

    private PaymentConfigResponse defaultConfigResponse(UUID accountId) {
        AccountPaymentConfig defaults = new AccountPaymentConfig();
        defaults.setAccountId(accountId);
        return toConfigResponse(defaults);
    }

    @Override
    public PaymentRailConfigResponse getPaymentRailConfig(String accountNumber, PaymentMode paymentMode) {
        BankAccount account = findByAccountNumberOrThrow(accountNumber);

        BigDecimal usedToday = accountLimitRepository
                .findByAccountIdAndTransactionType(account.getId(), paymentMode)
                .map(AccountLimit::getUsedTodayAmount)
                .orElse(BigDecimal.ZERO);

        return accountPaymentConfigRepository.findByAccountId(account.getId())
                .map(config -> buildRailConfigResponse(config, paymentMode, usedToday))
                .orElseGet(() -> PaymentRailConfigResponse.builder()
                        .enabled(false)
                        .perTransactionLimit(BigDecimal.ZERO)
                        .dailyLimit(BigDecimal.ZERO)
                        .usedToday(usedToday)
                        .remainingToday(BigDecimal.ZERO)
                        .build());
    }

    private PaymentRailConfigResponse buildRailConfigResponse(AccountPaymentConfig config, PaymentMode paymentMode, BigDecimal usedToday) {
        boolean enabled;
        BigDecimal perTransactionLimit;
        BigDecimal dailyLimit;

        switch (paymentMode) {
            case NEFT -> {
                enabled = config.isNeftEnabled();
                perTransactionLimit = config.getNeftPerTransactionLimit();
                dailyLimit = config.getNeftDailyLimit();
            }
            case RTGS -> {
                enabled = config.isRtgsEnabled();
                perTransactionLimit = config.getRtgsPerTransactionLimit();
                dailyLimit = config.getRtgsDailyLimit();
            }
            case IMPS -> {
                enabled = config.isImpsEnabled();
                perTransactionLimit = config.getImpsPerTransactionLimit();
                dailyLimit = config.getImpsDailyLimit();
            }
            case UPI -> {
                enabled = config.isUpiEnabled();
                perTransactionLimit = config.getUpiPerTransactionLimit();
                dailyLimit = config.getUpiDailyLimit();
            }
            default -> throw new IllegalArgumentException("Unsupported payment mode: " + paymentMode);
        }

        return PaymentRailConfigResponse.builder()
                .enabled(enabled)
                .perTransactionLimit(perTransactionLimit)
                .dailyLimit(dailyLimit)
                .usedToday(usedToday)
                .remainingToday(dailyLimit.subtract(usedToday))
                .build();
    }
}
