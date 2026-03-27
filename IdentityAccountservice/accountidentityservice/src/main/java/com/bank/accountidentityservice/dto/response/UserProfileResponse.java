package com.bank.accountidentityservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class UserProfileResponse {

    private String customerId;

    private String fullName;

    private LocalDate dob;

    private String aadhaarMasked;

    private String panNumber;

    private String email;

    private String phoneNumber;

    private String address;

    private String status;

    private List<AccountResponse> accounts;
}
