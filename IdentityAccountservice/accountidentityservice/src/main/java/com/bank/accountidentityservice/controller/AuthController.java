package com.bank.accountidentityservice.controller;

import com.bank.accountidentityservice.dto.request.LoginRequest;
import com.bank.accountidentityservice.dto.request.RegisterInitRequest;
import com.bank.accountidentityservice.dto.request.VerifyRegistrationOtpRequest;
import com.bank.accountidentityservice.dto.response.ApiResponse;
import com.bank.accountidentityservice.dto.response.LoginResponse;
import com.bank.accountidentityservice.dto.response.RegisterInitResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/init")
    public ResponseEntity<ApiResponse<RegisterInitResponse>> registerInit(
            @Valid @RequestBody RegisterInitRequest request) {

        RegisterInitResponse response = authService.initiateRegistration(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("OTP sent to your registered email.", response));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<UserProfileResponse>> verifyOtp(
            @Valid @RequestBody VerifyRegistrationOtpRequest request) {
        UserProfileResponse profile = authService.verifyRegistrationOtp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Registration successful! Welcome, " + profile.getFullName() + ".",
                        profile));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Login successful.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal String customerId) {
        authService.logout(customerId);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."));
    }
}
