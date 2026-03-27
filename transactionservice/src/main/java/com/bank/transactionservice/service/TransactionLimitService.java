package com.bank.transactionservice.service;

import com.bank.transactionservice.config.AppConfig;
import com.bank.transactionservice.entity.DailyTransactionCount;
import com.bank.transactionservice.exception.CustomExceptions.*;
import com.bank.transactionservice.repository.DailyTransactionCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Enforces two types of limits:
//
// 1. DAILY TRANSACTION COUNT LIMIT
//    - Maximum 16 transactions per account per day
//    - Applies to ALL transaction types: deposit, withdrawal, transfer
//    - Count resets at midnight every day (new row per day in DB)
//    - No cap on amount — only count is limited
//
// 2. NEW RECIPIENT LIMIT (for transfers only)
//    - If the destination account was created less than 24 hours ago
//      → maximum transfer amount is ₹1,00,000
//    - If the destination account is older than 24 hours
//      → no transfer amount limit
//    - This rule mirrors real bank behavior (e.g. SBI, HDFC new payee limit)
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLimitService {

    private final DailyTransactionCountRepository dailyCountRepository;
    private final AppConfig appConfig;
    // ── DAILY COUNT CHECK ─────────────────────────────────────────
    // Call this BEFORE executing any transaction.
    // Throws DailyLimitExceededException if limit reached.
    public void checkDailyTransactionCount(String accountNumber) {
        LocalDate today = LocalDate.now();

        DailyTransactionCount record = dailyCountRepository
                .findByAccountNumberAndCountDate(accountNumber, today)
                .orElse(DailyTransactionCount.builder()
                        .accountNumber(accountNumber)
                        .countDate(today)
                        .transactionCount(0)
                        .build());

        int current = record.getTransactionCount();
        int limit   = appConfig.getDailyTransactionLimit();

        if (current >= limit) {
            throw new DailyLimitExceededException(
                    String.format(
                            "Daily transaction limit reached. You have used %d/%d transactions today. " +
                                    "Limit resets at midnight.",
                            current, limit)
            );
        }

        log.info("Daily count check passed for {}: {}/{}", accountNumber, current + 1, limit);
    }

    // ── DAILY COUNT INCREMENT ─────────────────────────────────────
    // Call this AFTER a transaction succeeds.
    @Transactional
    public void incrementDailyCount(String accountNumber) {
        LocalDate today = LocalDate.now();

        DailyTransactionCount record = dailyCountRepository
                .findByAccountNumberAndCountDate(accountNumber, today)
                .orElse(DailyTransactionCount.builder()
                        .accountNumber(accountNumber)
                        .countDate(today)
                        .transactionCount(0)
                        .build());

        record.setTransactionCount(record.getTransactionCount() + 1);
        dailyCountRepository.save(record);

        log.info("Daily count incremented for {}: {}/{} transactions today",
                accountNumber, record.getTransactionCount(),
                appConfig.getDailyTransactionLimit());
    }

    // ── NEW RECIPIENT CHECK ───────────────────────────────────────
    // Returns true if the account is a "new" account (< 24 hrs old)
    public boolean isNewRecipient(LocalDateTime accountCreatedAt) {
        LocalDateTime threshold = LocalDateTime.now()
                .minusHours(appConfig.getNewRecipientThresholdHours());
        return accountCreatedAt.isAfter(threshold);
    }

    // Checks new recipient limit and throws if exceeded
    // Call this BEFORE executing a transfer
    public void checkNewRecipientLimit(LocalDateTime recipientCreatedAt,
                                       BigDecimal transferAmount) {
        if (!isNewRecipient(recipientCreatedAt)) {
            return; // Existing recipient — no limit
        }

        BigDecimal limit = appConfig.getNewRecipientTransferLimit();

        if (transferAmount.compareTo(limit) > 0) {
            throw new NewRecipientLimitExceededException(
                    String.format(
                            "This is a new recipient account (added less than %d hours ago). " +
                                    "Maximum transfer amount is ₹%.2f for new recipients. " +
                                    "The limit will be lifted after 24 hours.",
                            appConfig.getNewRecipientThresholdHours(),
                            limit)
            );
        }

        log.info("New recipient limit check passed: ₹{} <= ₹{}", transferAmount, limit);
    }

    // Returns remaining transactions for today for an account
    public int getRemainingTransactions(String accountNumber) {
        LocalDate today = LocalDate.now();
        int used = dailyCountRepository
                .findByAccountNumberAndCountDate(accountNumber, today)
                .map(DailyTransactionCount::getTransactionCount)
                .orElse(0);
        return appConfig.getDailyTransactionLimit() - used;
    }
}