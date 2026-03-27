package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.config.AppConfig;
import com.bank.accountidentityservice.dto.request.LoginRequest;
import com.bank.accountidentityservice.dto.request.RegisterInitRequest;
import com.bank.accountidentityservice.dto.request.VerifyRegistrationOtpRequest;
import com.bank.accountidentityservice.dto.response.AccountResponse;
import com.bank.accountidentityservice.dto.response.LoginResponse;
import com.bank.accountidentityservice.dto.response.RegisterInitResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.entity.Account;
import com.bank.accountidentityservice.entity.OtpStore;
import com.bank.accountidentityservice.entity.RefreshToken;
import com.bank.accountidentityservice.entity.Role;
import com.bank.accountidentityservice.entity.User;
import com.bank.accountidentityservice.entity.UserRole;
import com.bank.accountidentityservice.exception.CustomExceptions.AccountLockedOrDisabledException;
import com.bank.accountidentityservice.exception.CustomExceptions.InvalidCredentialsException;
import com.bank.accountidentityservice.exception.CustomExceptions.InvalidOtpException;
import com.bank.accountidentityservice.exception.CustomExceptions.OtpExpiredException;
import com.bank.accountidentityservice.exception.CustomExceptions.UserAlreadyExistsException;
import com.bank.accountidentityservice.exception.CustomExceptions.UserNotFoundException;
import com.bank.accountidentityservice.repository.AccountRepository;
import com.bank.accountidentityservice.repository.OtpStoreRepository;
import com.bank.accountidentityservice.repository.RefreshTokenRepository;
import com.bank.accountidentityservice.repository.RoleRepository;
import com.bank.accountidentityservice.repository.UserRepository;
import com.bank.accountidentityservice.util.AccountNumberGenerator;
import com.bank.accountidentityservice.util.CustomerIdGenerator;
import com.bank.accountidentityservice.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final OtpStoreRepository otpStoreRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final OtpGenerator otpGenerator;
    private final CustomerIdGenerator customerIdGenerator;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AppConfig appConfig;

    private final Map<String, RegisterInitRequest> pendingRegistrations = new ConcurrentHashMap<>();

    @Transactional
    public RegisterInitResponse initiateRegistration(RegisterInitRequest request) {

        if (userRepository.existsByEmail(request.getEmail()))
            throw new UserAlreadyExistsException("This email address is already registered.");

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new UserAlreadyExistsException("This phone number is already registered.");

        if (userRepository.existsByPanNumber(request.getPanNumber().toUpperCase()))
            throw new UserAlreadyExistsException("This PAN number is already registered.");

        request.setPanNumber(request.getPanNumber().toUpperCase());

        pendingRegistrations.put(request.getEmail(), request);

        String rawOtp = otpGenerator.generate();

        otpStoreRepository.invalidateAll("PENDING_REG", OtpStore.OtpPurpose.REGISTRATION);

        OtpStore otpRecord = OtpStore.builder()
                .customerId("PENDING_REG")
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(OtpStore.OtpPurpose.REGISTRATION)
                .refValue(request.getEmail())
                .expiresAt(LocalDateTime.now().plusMinutes(appConfig.getOtpExpiryMinutes()))
                .build();
        otpStoreRepository.save(otpRecord);

        emailService.sendRegistrationOtp(request.getEmail(), request.getFullName(), rawOtp);

        log.info("Registration OTP sent to: {}", request.getEmail());

        return RegisterInitResponse.builder()
                .message("OTP sent to " + maskEmail(request.getEmail()) +
                         ". Valid for " + appConfig.getOtpExpiryMinutes() + " minutes.")
                .email(maskEmail(request.getEmail()))
                .build();
    }

    @Transactional
    public UserProfileResponse verifyRegistrationOtp(VerifyRegistrationOtpRequest request) {

        RegisterInitRequest pending = pendingRegistrations.get(request.getEmail());
        if (pending == null)
            throw new UserNotFoundException(
                    "No pending registration found for this email. Please start registration again.");

        List<OtpStore> validOtps = otpStoreRepository
                .findValidOtps("PENDING_REG", OtpStore.OtpPurpose.REGISTRATION)
                .stream()
                .filter(o -> request.getEmail().equals(o.getRefValue()))
                .toList();

        if (validOtps.isEmpty())
            throw new OtpExpiredException("OTP has expired or was not found. Please register again.");

        OtpStore otpRecord = validOtps.get(0);
        if (!passwordEncoder.matches(request.getOtp(), otpRecord.getOtpHash()))
            throw new InvalidOtpException("Incorrect OTP. Please check and try again.");

        otpRecord.setUsedAt(LocalDateTime.now());
        otpStoreRepository.save(otpRecord);

        String customerId   = customerIdGenerator.generate();
        String accountNumber = accountNumberGenerator.generate(customerId, pending.getAccountType());

        Role customerRole = roleRepository.findByRoleName(Role.RoleName.CUSTOMER)
                .orElseThrow(() -> new RuntimeException(
                        "CUSTOMER role not found in DB. Ensure DataInitializer ran on startup."));

        User user = User.builder()
                .customerId(customerId)
                .fullName(pending.getFullName())
                .dob(pending.getDob())
                .aadhaarHash(passwordEncoder.encode(pending.getAadhaarNumber()))
                .aadhaarLast4(pending.getAadhaarNumber().substring(8))
                .panNumber(pending.getPanNumber())
                .email(pending.getEmail())
                .phoneNumber(pending.getPhoneNumber())
                .address(pending.getAddress())
                .passwordHash(passwordEncoder.encode(pending.getPassword()))
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        UserRole userRole = new UserRole(user, customerRole);
        user.getUserRoles().add(userRole);
        userRepository.save(user);

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customerId(customerId)
                .accountType(pending.getAccountType())
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.PENDING_PIN)
                .build();
        accountRepository.save(account);

        pendingRegistrations.remove(request.getEmail());

        emailService.sendWelcomeEmail(
                user.getEmail(),
                user.getFullName(),
                customerId,
                accountNumber,
                pending.getAccountType().name()
        );

        log.info("Registration complete — customerId: {}, accountNumber: {}", customerId, accountNumber);

        return buildProfileResponse(user, List.of(account));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByCustomerId(request.getCustomerId())
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid Customer ID or password. Please try again."));

        if (user.getStatus() == User.UserStatus.LOCKED)
            throw new AccountLockedOrDisabledException(
                    "Your account is locked due to multiple failed attempts. Please contact support.");

        if (user.getStatus() == User.UserStatus.DISABLED)
            throw new AccountLockedOrDisabledException(
                    "Your account has been disabled. Please contact our support team.");

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new InvalidCredentialsException(
                    "Invalid Customer ID or password. Please try again.");

        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getRoleName().name())
                .toList();

        String accessToken    = jwtService.generateAccessToken(user.getCustomerId(), roles);
        String rawRefreshToken = jwtService.generateRefreshToken(user.getCustomerId());

        RefreshToken refreshToken = RefreshToken.builder()
                .customerId(user.getCustomerId())
                .tokenHash(passwordEncoder.encode(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        List<Account> accounts = accountRepository.findByCustomerId(user.getCustomerId());

        log.info("User logged in: {}", user.getCustomerId());

        return LoginResponse.builder()
                .customerId(user.getCustomerId())
                .fullName(user.getFullName())
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .accessTokenExpiresInMs(jwtService.getAccessTokenExpiryMs())
                .accounts(accounts.stream().map(this::mapAccount).toList())
                .build();
    }

    @Transactional
    public void logout(String customerId) {

        refreshTokenRepository.revokeAllByCustomerId(customerId);
        log.info("User logged out: {}", customerId);
    }

    public UserProfileResponse buildProfileResponse(User user, List<Account> accounts) {
        return UserProfileResponse.builder()
                .customerId(user.getCustomerId())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .aadhaarMasked("XXXX XXXX " + user.getAadhaarLast4())
                .panNumber(user.getPanNumber())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .status(user.getStatus().name())
                .accounts(accounts.stream().map(this::mapAccount).toList())
                .build();
    }

    public AccountResponse mapAccount(Account account) {
        return AccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .status(account.getStatus())
                .pinSet(account.getPinHash() != null)
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return email;
        return email.charAt(0) + "***" + email.substring(atIndex - 1);
    }
}
