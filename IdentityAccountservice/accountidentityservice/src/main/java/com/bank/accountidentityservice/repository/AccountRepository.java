package com.bank.accountidentityservice.repository;

import com.bank.accountidentityservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomerId(String customerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    long countByCustomerIdAndAccountType(String customerId, Account.AccountType accountType);
}
