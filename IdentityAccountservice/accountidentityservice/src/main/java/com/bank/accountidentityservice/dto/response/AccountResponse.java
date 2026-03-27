package com.bank.accountidentityservice.dto.response;

import com.bank.accountidentityservice.entity.Account;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountResponse {

    private String accountNumber;

    private Account.AccountType accountType;

    private BigDecimal balance;

    private Account.AccountStatus status;

    private boolean pinSet;

    private LocalDateTime createdAt;
}
