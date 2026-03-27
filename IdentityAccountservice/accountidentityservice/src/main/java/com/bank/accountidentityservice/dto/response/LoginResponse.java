package com.bank.accountidentityservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {

    private String customerId;

    private String fullName;

    private String accessToken;

    private String refreshToken;

    private long accessTokenExpiresInMs;

    private List<AccountResponse> accounts;
}
