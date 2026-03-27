package com.bank.transactionservice.service;

import com.bank.transactionservice.dto.request.*;
import com.bank.transactionservice.dto.response.TransactionResponse;
import com.bank.transactionservice.entity.Transaction;
import com.bank.transactionservice.entity.Transaction.TransactionStatus;
import com.bank.transactionservice.entity.Transaction.TransactionType;
import com.bank.transactionservice.exception.CustomExceptions.*;
import com.bank.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository       transactionRepository;
    private final AccountVerificationService  accountVerificationService;
    private final FeeCalculationService       feeCalculationService;
    private final TransactionLimitService     limitService;
    private final AuditService               auditService;

    // ═══════════════════════════════════════════════════════════════
    // DEPOSIT
    // Rules applied:
    //   ✅ Daily count limit (16/day)
    //   ❌ No fee on deposits
    //   ❌ No PIN required
    //   ❌ No new-recipient check (deposit = incoming money)
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    public TransactionResponse deposit(DepositRequest request,
                                       String customerId,
                                       String jwtToken) {

        // 1. Check daily transaction count limit
        limitService.checkDailyTransactionCount(request.getAccountNumber());

        // 2. Get current balance — also validates account exists and is ACTIVE
        BigDecimal currentBalance = accountVerificationService
                .getBalance(request.getAccountNumber(), jwtToken);

        // 3. No fee on deposits — totalDebited = amount
        BigDecimal newBalance = currentBalance.add(request.getAmount());

        // 4. Update balance in account identity service
        accountVerificationService.updateBalance(
                request.getAccountNumber(), newBalance, jwtToken);

        // 5. Save transaction record
        Transaction txn = Transaction.builder()
                .transactionRef(generateRef())
                .toAccount(request.getAccountNumber())
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .feeAmount(BigDecimal.ZERO)
                .feeRate(BigDecimal.ZERO)
                .totalDebited(request.getAmount())
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription() : "Cash Deposit")
                .initiatedBy(customerId)
                .build();
        transactionRepository.save(txn);

        // 6. Increment daily count after successful save
        limitService.incrementDailyCount(request.getAccountNumber());

        // 7. Send audit event async — never blocks response
        auditService.logDeposit(txn);

        log.info("DEPOSIT ₹{} → {} | Ref: {} | New balance: ₹{}",
                request.getAmount(), request.getAccountNumber(),
                txn.getTransactionRef(), newBalance);

        return TransactionResponse.from(txn);
    }

    // ═══════════════════════════════════════════════════════════════
    // WITHDRAWAL
    // Rules applied:
    //   ✅ Daily count limit (16/day)
    //   ✅ Fee applied (2.5% if < ₹10K, else 5%)
    //   ✅ PIN required
    //   ✅ Sufficient balance check (amount + fee must be available)
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request,
                                        String customerId,
                                        String jwtToken) {

        // 1. Check daily transaction count limit
        limitService.checkDailyTransactionCount(request.getAccountNumber());

        // 2. Verify PIN — throws InvalidPinException if wrong
        BigDecimal currentBalance = accountVerificationService
                .verifyPinAndGetBalance(request.getAccountNumber(),
                        request.getPin(), jwtToken);

        // 3. Calculate fee
        BigDecimal feeRate    = feeCalculationService.getFeeRate(request.getAmount());
        BigDecimal feeAmount  = feeCalculationService.calculateFee(request.getAmount());
        BigDecimal totalDebit = request.getAmount().add(feeAmount);

        log.info("Withdrawal fee calc: {}", feeCalculationService.describeFee(request.getAmount()));

        // 4. Check sufficient balance — user must have enough for BOTH amount AND fee
        if (currentBalance.compareTo(totalDebit) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Insufficient balance. You need ₹%.2f (₹%.2f + ₹%.2f fee @ %.1f%%). " +
                            "Available: ₹%.2f",
                    totalDebit, request.getAmount(), feeAmount, feeRate, currentBalance));
        }

        // 5. Deduct total (amount + fee) from balance
        BigDecimal newBalance = currentBalance.subtract(totalDebit);

        // 6. Update balance in account service
        accountVerificationService.updateBalance(
                request.getAccountNumber(), newBalance, jwtToken);

        // 7. Save transaction
        Transaction txn = Transaction.builder()
                .transactionRef(generateRef())
                .fromAccount(request.getAccountNumber())
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .feeAmount(feeAmount)
                .feeRate(feeRate)
                .totalDebited(totalDebit)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription() : "Cash Withdrawal")
                .initiatedBy(customerId)
                .build();
        transactionRepository.save(txn);

        // 8. Increment daily count
        limitService.incrementDailyCount(request.getAccountNumber());

        // 9. Send audit event async
        auditService.logWithdrawal(txn);

        log.info("WITHDRAWAL ₹{} from {} | Fee: ₹{} | Total: ₹{} | Ref: {} | Balance: ₹{}",
                request.getAmount(), request.getAccountNumber(),
                feeAmount, totalDebit, txn.getTransactionRef(), newBalance);

        return TransactionResponse.from(txn);
    }

    // ═══════════════════════════════════════════════════════════════
    // TRANSFER
    // Rules applied:
    //   ✅ Daily count limit on FROM account (16/day)
    //   ✅ Fee applied (2.5% if < ₹10K, else 5%) — deducted from FROM account
    //   ✅ PIN required on FROM account
    //   ✅ Sufficient balance check (amount + fee)
    //   ✅ New recipient limit: if TO account < 24 hrs old → max ₹1,00,000
    //   ✅ Cannot transfer to same account
    //   ✅ TO account must be ACTIVE
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    public TransactionResponse transfer(TransferRequest request,
                                        String customerId,
                                        String jwtToken) {

        // 0. Cannot transfer to self
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new SameAccountTransferException(
                    "Cannot transfer to the same account.");
        }

        // 1. Check daily count on source account
        limitService.checkDailyTransactionCount(request.getFromAccount());

        // 2. Verify PIN on source account and get balance
        BigDecimal fromBalance = accountVerificationService
                .verifyPinAndGetBalance(request.getFromAccount(),
                        request.getPin(), jwtToken);

        // 3. Calculate fee on transfer amount
        BigDecimal feeRate    = feeCalculationService.getFeeRate(request.getAmount());
        BigDecimal feeAmount  = feeCalculationService.calculateFee(request.getAmount());
        BigDecimal totalDebit = request.getAmount().add(feeAmount);

        log.info("Transfer fee calc: {}", feeCalculationService.describeFee(request.getAmount()));

        // 4. Check sufficient balance in source account
        if (fromBalance.compareTo(totalDebit) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Insufficient balance. You need ₹%.2f (₹%.2f + ₹%.2f fee @ %.1f%%). " +
                            "Available: ₹%.2f",
                    totalDebit, request.getAmount(), feeAmount, feeRate, fromBalance));
        }

        // 5. Get destination account balance — validates it exists and is ACTIVE
        BigDecimal toBalance = accountVerificationService
                .getBalance(request.getToAccount(), jwtToken);

        // 6. NEW RECIPIENT CHECK
        // Fetch when the destination account was created
        LocalDateTime recipientCreatedAt = accountVerificationService
                .getAccountCreatedAt(request.getToAccount(), jwtToken);

        boolean isNewRecipient = limitService.isNewRecipient(recipientCreatedAt);

        // Throws NewRecipientLimitExceededException if amount > ₹1,00,000 for new recipient
        limitService.checkNewRecipientLimit(recipientCreatedAt, request.getAmount());

        // 7. Calculate new balances
        // Source: deduct amount + fee
        BigDecimal newFromBalance = fromBalance.subtract(totalDebit);
        // Destination: add only the amount (fee stays with the bank, not transferred)
        BigDecimal newToBalance   = toBalance.add(request.getAmount());

        // 8. Update both balances atomically (within this @Transactional method)
        accountVerificationService.updateBalance(
                request.getFromAccount(), newFromBalance, jwtToken);
        accountVerificationService.updateBalance(
                request.getToAccount(), newToBalance, jwtToken);

        // 9. Save transaction record
        Transaction txn = Transaction.builder()
                .transactionRef(generateRef())
                .fromAccount(request.getFromAccount())
                .toAccount(request.getToAccount())
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .feeAmount(feeAmount)
                .feeRate(feeRate)
                .totalDebited(totalDebit)
                .balanceBefore(fromBalance)
                .balanceAfter(newFromBalance)
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription() : "Fund Transfer")
                .initiatedBy(customerId)
                .newRecipientFlag(isNewRecipient)
                .build();
        transactionRepository.save(txn);

        // 10. Increment daily count for source account
        limitService.incrementDailyCount(request.getFromAccount());

        // 11. Send audit event async
        auditService.logTransfer(txn);

        log.info("TRANSFER ₹{} from {} → {} | Fee: ₹{} | Total debit: ₹{} | Ref: {} | From balance: ₹{} | New recipient: {}",
                request.getAmount(), request.getFromAccount(), request.getToAccount(),
                feeAmount, totalDebit, txn.getTransactionRef(), newFromBalance, isNewRecipient);

        return TransactionResponse.from(txn);
    }

    // ═══════════════════════════════════════════════════════════════
    // HISTORY — paginated list of all transactions for an account
    // ═══════════════════════════════════════════════════════════════
    public Page<TransactionResponse> getHistory(String accountNumber,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        return transactionRepository.findByAccount(accountNumber, pageable)
                .map(TransactionResponse::from);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATEMENT — transactions within a date range
    // ═══════════════════════════════════════════════════════════════
    public List<TransactionResponse> getStatement(String accountNumber,
                                                  LocalDate from,
                                                  LocalDate to) {
        return transactionRepository.findByAccountAndDateRange(
                        accountNumber,
                        from.atStartOfDay(),
                        to.atTime(23, 59, 59))
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // GET SINGLE TRANSACTION BY REFERENCE
    // ═══════════════════════════════════════════════════════════════
    public TransactionResponse getByRef(String transactionRef) {
        Transaction txn = transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found: " + transactionRef));
        return TransactionResponse.from(txn);
    }

    // ═══════════════════════════════════════════════════════════════
    // REMAINING TRANSACTIONS TODAY
    // ═══════════════════════════════════════════════════════════════
    public int getRemainingTransactions(String accountNumber) {
        return limitService.getRemainingTransactions(accountNumber);
    }

    // ── Reference number generator ───────────────────────────────
    // Format: TXN20260323001 (TXN + date + 4-digit sequence)
    private String generateRef() {

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        long seq = transactionRepository.countTodayTransactions(startOfDay, endOfDay) + 1;

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return "TXN" + date + String.format("%04d", seq);
    }
}