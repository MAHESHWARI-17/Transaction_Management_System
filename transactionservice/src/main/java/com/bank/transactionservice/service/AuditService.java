package com.bank.transactionservice.service;

import com.bank.transactionservice.dto.audit.AuditEventPayload;
import com.bank.transactionservice.entity.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// Sends audit events to the Audit Service after every transaction.
// Called asynchronously (@Async) so it never blocks the transaction API response.
//
// Authentication: uses X-Internal-API-Key header — NOT a JWT.
// The audit service checks this key against its internal.api-key property.
//
// Audit Service endpoint: POST http://localhost:8083/api/v1/audit/events
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final RestTemplate  restTemplate;
    private final ObjectMapper  objectMapper;

    // audit-service base URL — configurable in application.yml
    @Value("${audit-service.url:http://localhost:8083}")
    private String auditServiceUrl;

    // Internal API key — must match audit-service internal.api-key value
    @Value("${audit-service.internal-api-key:internal-service-secret-key}")
    private String internalApiKey;

    // ── Called after DEPOSIT ──────────────────────────────────────
    @Async
    public void logDeposit(Transaction txn) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("transactionRef",  txn.getTransactionRef());
        meta.put("toAccount",       txn.getToAccount());
        meta.put("amount",          txn.getAmount().toPlainString());
        meta.put("feeAmount",       txn.getFeeAmount().toPlainString());
        meta.put("totalDebited",    txn.getTotalDebited().toPlainString());
        meta.put("balanceBefore",   txn.getBalanceBefore().toPlainString());
        meta.put("balanceAfter",    txn.getBalanceAfter().toPlainString());
        meta.put("description",     txn.getDescription());
        meta.put("status",          txn.getStatus().name());

        send(AuditEventPayload.builder()
                .serviceName("TRANSACTIONSERVICE")
                .action(txn.getStatus() == Transaction.TransactionStatus.SUCCESS
                        ? "DEPOSIT_SUCCESS" : "DEPOSIT_FAILED")
                .severity(txn.getStatus() == Transaction.TransactionStatus.SUCCESS
                        ? "INFO" : "ERROR")
                .actorUserId(txn.getInitiatedBy())
                .actorRole("CUSTOMER")
                .entityType("TRANSACTION")
                .entityId(txn.getTransactionId() != null ? txn.getTransactionId().toString() : null)
                .metadata(meta)
                .occurredAt(txn.getCreatedAt())
                .build());
    }

    // ── Called after WITHDRAWAL ───────────────────────────────────
    @Async
    public void logWithdrawal(Transaction txn) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("transactionRef",  txn.getTransactionRef());
        meta.put("fromAccount",     txn.getFromAccount());
        meta.put("amount",          txn.getAmount().toPlainString());
        meta.put("feeAmount",       txn.getFeeAmount().toPlainString());
        meta.put("feeRate",         txn.getFeeRate().toPlainString());
        meta.put("totalDebited",    txn.getTotalDebited().toPlainString());
        meta.put("balanceBefore",   txn.getBalanceBefore().toPlainString());
        meta.put("balanceAfter",    txn.getBalanceAfter().toPlainString());
        meta.put("description",     txn.getDescription());
        meta.put("status",          txn.getStatus().name());

        send(AuditEventPayload.builder()
                .serviceName("TRANSACTIONSERVICE")
                .action(txn.getStatus() == Transaction.TransactionStatus.SUCCESS
                        ? "WITHDRAWAL_SUCCESS" : "WITHDRAWAL_FAILED")
                .severity(txn.getStatus() == Transaction.TransactionStatus.SUCCESS
                        ? "INFO" : "ERROR")
                .actorUserId(txn.getInitiatedBy())
                .actorRole("CUSTOMER")
                .entityType("TRANSACTION")
                .entityId(txn.getTransactionId() != null ? txn.getTransactionId().toString() : null)
                .metadata(meta)
                .occurredAt(txn.getCreatedAt())
                .build());
    }

    // ── Called after TRANSFER ─────────────────────────────────────
    @Async
    public void logTransfer(Transaction txn) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("transactionRef",    txn.getTransactionRef());
        meta.put("fromAccount",       txn.getFromAccount());
        meta.put("toAccount",         txn.getToAccount());
        meta.put("amount",            txn.getAmount().toPlainString());
        meta.put("feeAmount",         txn.getFeeAmount().toPlainString());
        meta.put("feeRate",           txn.getFeeRate().toPlainString());
        meta.put("totalDebited",      txn.getTotalDebited().toPlainString());
        meta.put("balanceBefore",     txn.getBalanceBefore().toPlainString());
        meta.put("balanceAfter",      txn.getBalanceAfter().toPlainString());
        meta.put("description",       txn.getDescription());
        meta.put("status",            txn.getStatus().name());
        meta.put("newRecipientFlag",  txn.isNewRecipientFlag());

        // Classify large transfers as WARN for compliance monitoring
        String severity = "INFO";
        if (txn.getAmount().doubleValue() >= 50000) severity = "WARN";
        if (txn.getStatus() == Transaction.TransactionStatus.FAILED) severity = "ERROR";

        send(AuditEventPayload.builder()
                .serviceName("TRANSACTIONSERVICE")
                .action(txn.getStatus() == Transaction.TransactionStatus.SUCCESS
                        ? "TRANSFER_SUCCESS" : "TRANSFER_FAILED")
                .severity(severity)
                .actorUserId(txn.getInitiatedBy())
                .actorRole("CUSTOMER")
                .entityType("TRANSACTION")
                .entityId(txn.getTransactionId() != null ? txn.getTransactionId().toString() : null)
                .metadata(meta)
                .occurredAt(txn.getCreatedAt())
                .build());
    }

    // ── Called when daily limit is hit ────────────────────────────
    @Async
    public void logDailyLimitReached(String customerId, String accountNumber) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("accountNumber", accountNumber);
        meta.put("customerId",    customerId);
        meta.put("limitReached",  16);
        meta.put("message",       "Customer reached daily transaction limit of 16");

        send(AuditEventPayload.builder()
                .serviceName("TRANSACTIONSERVICE")
                .action("DAILY_LIMIT_REACHED")
                .severity("WARN")
                .actorUserId(customerId)
                .actorRole("CUSTOMER")
                .entityType("ACCOUNT")
                .metadata(meta)
                .build());
    }

    // ── Called when new-recipient limit is exceeded ───────────────
    @Async
    public void logNewRecipientLimitViolation(String customerId, String toAccount, String amount) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("toAccount",       toAccount);
        meta.put("attemptedAmount", amount);
        meta.put("limit",           "100000.00");
        meta.put("reason",          "Recipient account less than 24 hours old");

        send(AuditEventPayload.builder()
                .serviceName("TRANSACTIONSERVICE")
                .action("NEW_RECIPIENT_LIMIT_EXCEEDED")
                .severity("WARN")
                .actorUserId(customerId)
                .actorRole("CUSTOMER")
                .entityType("TRANSACTION")
                .metadata(meta)
                .build());
    }

    // ── Internal HTTP call ────────────────────────────────────────
    private void send(AuditEventPayload payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // This header is the ONLY authentication the audit service requires
            // It matches audit-service internal.api-key in application.yml
            headers.set("X-Internal-API-Key", internalApiKey);

            HttpEntity<AuditEventPayload> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    auditServiceUrl + "/api/v1/audit/events",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Audit event sent: [{}] {} → HTTP {}",
                    payload.getSeverity(), payload.getAction(),
                    response.getStatusCode());

        } catch (Exception e) {
            // NEVER let audit failure affect transaction — just log the error
            log.error("Failed to send audit event [{}] {} — Reason: {}",
                    payload.getSeverity(), payload.getAction(), e.getMessage());
        }
    }
}