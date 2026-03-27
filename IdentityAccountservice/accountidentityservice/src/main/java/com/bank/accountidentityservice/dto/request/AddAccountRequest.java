package com.bank.accountidentityservice.dto.request;

import com.bank.accountidentityservice.entity.Account;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddAccountRequest {

    @NotNull(message = "Account type is required — choose SAVINGS or CURRENT")
    private Account.AccountType accountType;
}
