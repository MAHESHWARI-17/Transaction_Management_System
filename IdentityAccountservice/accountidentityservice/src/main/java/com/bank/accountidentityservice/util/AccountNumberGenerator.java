package com.bank.accountidentityservice.util;

import com.bank.accountidentityservice.entity.Account;
import com.bank.accountidentityservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    public String generate(String customerId, Account.AccountType type) {

        String prefix = (type == Account.AccountType.SAVINGS) ? "SB" : "CA";

        String base = prefix + customerId;

        long count = accountRepository.countByCustomerIdAndAccountType(customerId, type);

        if (count == 0) {

            return base;
        }

        return base + "-" + (count + 1);
    }
}
