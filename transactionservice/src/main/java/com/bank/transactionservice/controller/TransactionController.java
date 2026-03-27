package com.bank.transactionservice.controller;

import com.bank.transactionservice.dto.request.*;
import com.bank.transactionservice.dto.response.*;
import com.bank.transactionservice.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // ── POST /transactions/deposit ────────────────────────────────
    // No PIN required. Fee: NONE.
    // Body: { "accountNumber": "SB100000000001", "amount": 5000, "description": "Salary" }
    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal String customerId,
            HttpServletRequest httpRequest) {

        TransactionResponse response = transactionService.deposit(
                request, customerId, extractToken(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit successful.", response));
    }

    // ── POST /transactions/withdraw ───────────────────────────────
    // PIN required. Fee: 2.5% if < ₹10K, else 5%.
    // Body: { "accountNumber": "SB100000000001", "pin": "1234", "amount": 2000 }
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @AuthenticationPrincipal String customerId,
            HttpServletRequest httpRequest) {

        TransactionResponse response = transactionService.withdraw(
                request, customerId, extractToken(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful.", response));
    }

    // ── POST /transactions/transfer ───────────────────────────────
    // PIN required. Fee: 2.5% if < ₹10K, else 5%.
    // New recipient (< 24 hrs): max ₹1,00,000.
    // Body: { "fromAccount": "SB100000000001", "toAccount": "SB100000000002",
    //         "pin": "1234", "amount": 3000, "description": "Rent" }
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal String customerId,
            HttpServletRequest httpRequest) {

        TransactionResponse response = transactionService.transfer(
                request, customerId, extractToken(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Transfer successful.", response));
    }

    // ── GET /transactions/history/{accountNumber} ─────────────────
    // Paginated transaction history for an account
    // ?page=0&size=10 (defaults)
    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getHistory(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<TransactionResponse> history =
                transactionService.getHistory(accountNumber, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Transaction history fetched successfully.", history));
    }

    // ── GET /transactions/statement/{accountNumber} ───────────────
    // Transactions between two dates (for bank statements)
    // ?from=2026-03-01&to=2026-03-31
    @GetMapping("/statement/{accountNumber}")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getStatement(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<TransactionResponse> statement =
                transactionService.getStatement(accountNumber, from, to);
        return ResponseEntity.ok(ApiResponse.success(
                "Statement fetched for " + from + " to " + to + ".", statement));
    }

    // ── GET /transactions/{ref} ───────────────────────────────────
    // Fetch a single transaction by reference number e.g. TXN202603230001
    @GetMapping("/{transactionRef}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByRef(
            @PathVariable String transactionRef) {

        TransactionResponse txn = transactionService.getByRef(transactionRef);
        return ResponseEntity.ok(ApiResponse.success("Transaction found.", txn));
    }

    // ── GET /transactions/remaining/{accountNumber} ───────────────
    // How many transactions remain today for this account
    @GetMapping("/remaining/{accountNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRemaining(
            @PathVariable String accountNumber) {

        int remaining = transactionService.getRemainingTransactions(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(
                "Remaining transactions today.",
                Map.of(
                    "accountNumber", accountNumber,
                    "remainingToday", remaining,
                    "dailyLimit", 16
                )));
    }

    // ── Helper: extract raw JWT from Authorization header ─────────
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer "))
            return header.substring(7);
        return "";
    }
}
