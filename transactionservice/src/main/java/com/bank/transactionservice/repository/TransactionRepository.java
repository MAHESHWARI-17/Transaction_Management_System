package com.bank.transactionservice.repository;

import com.bank.transactionservice.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // All transactions involving this account (sent or received), newest first
    @Query("SELECT t FROM Transaction t WHERE t.fromAccount = :accNo OR t.toAccount = :accNo ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccount(@Param("accNo") String accNo, Pageable pageable);

    // Transactions within a date range for a specific account
    @Query("SELECT t FROM Transaction t WHERE (t.fromAccount = :accNo OR t.toAccount = :accNo) AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountAndDateRange(
            @Param("accNo") String accNo,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    Optional<Transaction> findByTransactionRef(String transactionRef);

    // Count all transactions today — used to generate sequential reference numbers
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt >= :startOfDay AND t.createdAt < :endOfDay")
    long countTodayTransactions(
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
            @Param("endOfDay") java.time.LocalDateTime endOfDay);
}
