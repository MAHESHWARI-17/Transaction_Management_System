package com.bank.accountidentityservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterInitResponse {

    private String message;

    private String email;
}
