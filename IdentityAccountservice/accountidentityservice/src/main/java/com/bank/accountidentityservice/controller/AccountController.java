package com.bank.accountidentityservice.controller;

import com.bank.accountidentityservice.dto.request.AddAccountRequest;
import com.bank.accountidentityservice.dto.request.RequestPinOtpRequest;
import com.bank.accountidentityservice.dto.request.SetPinRequest;
import com.bank.accountidentityservice.dto.response.AccountResponse;
import com.bank.accountidentityservice.dto.response.ApiResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal String customerId) {

        UserProfileResponse profile = accountService.getProfile(customerId);
        return ResponseEntity.ok(
                ApiResponse.success("Profile fetched successfully.", profile));
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<AccountResponse>> addAccount(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody AddAccountRequest request) {
        AccountResponse account = accountService.addAccount(customerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Account added successfully. Please set your PIN to activate it.",
                        account));
    }

    @PostMapping("/pin/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPinOtp(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody RequestPinOtpRequest request) {
        accountService.requestPinOtp(customerId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "OTP sent to your registered email. Valid for " +
                        "10 minutes. Please check your inbox."));
    }

    @PostMapping("/pin/set")
    public ResponseEntity<ApiResponse<AccountResponse>> setPin(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody SetPinRequest request) {
        AccountResponse account = accountService.setPin(customerId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "PIN set successfully. Your account is now ACTIVE and ready to use.",
                        account));
    }


}
