package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SetPinRequest {

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    private String otp;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits")
    private String pin;

    @NotBlank(message = "Confirm PIN is required")
    private String confirmPin;
}
