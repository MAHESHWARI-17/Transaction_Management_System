package com.bank.transactionservice.service;

import com.bank.transactionservice.exception.CustomExceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

// Calls the Account Identity Service (via API Gateway) to:
// 1. Verify PIN and get account balance
// 2. Get balance without PIN (for deposits)
// 3. Update balance after a transaction
// 4. Get account creation time (for new-recipient check)
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountVerificationService {

    private final RestTemplate restTemplate;

    @Value("${account-service.url}")
    private String accountServiceUrl;

    // ── Verify PIN → returns current balance if PIN correct ──────
    // Throws InvalidPinException if PIN is wrong
    // Throws AccountNotActiveException if account is not ACTIVE
    public BigDecimal verifyPinAndGetBalance(String accountNumber,
                                              String pin,
                                              String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtToken);

            Map<String, String> body = Map.of(
                    "accountNumber", accountNumber,
                    "pin", pin
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    accountServiceUrl + "/accounts/verify-pin",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> data = extractData(response);
            return new BigDecimal(data.get("balance").toString());

        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvalidPinException("Incorrect PIN. Please try again.");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new AccountNotActiveException(
                    "Account is not active. Please contact support.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new AccountNotFoundException(
                    "Account not found: " + accountNumber);
        } catch (InvalidPinException | AccountNotActiveException | AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("PIN verification failed for {}: {}", accountNumber, e.getMessage());
            throw new RuntimeException("Unable to verify PIN. Please try again.");
        }
    }

    // ── Get balance without PIN ───────────────────────────────────
    // Used for deposits (no PIN required) and for transfer destination
    public BigDecimal getBalance(String accountNumber, String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    accountServiceUrl + "/accounts/balance/" + accountNumber,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map<String, Object> data = extractData(response);
            return new BigDecimal(data.get("balance").toString());

        } catch (HttpClientErrorException.NotFound e) {
            throw new AccountNotFoundException("Account not found: " + accountNumber);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new AccountNotActiveException(
                    "Account " + accountNumber + " is not active.");
        } catch (AccountNotFoundException | AccountNotActiveException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get balance for {}: {}", accountNumber, e.getMessage());
            throw new RuntimeException("Unable to fetch account details.");
        }
    }

    // ── Get account creation time ─────────────────────────────────
    // Used to check if recipient is a "new account" (< 24 hrs old)
    public LocalDateTime getAccountCreatedAt(String accountNumber, String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    accountServiceUrl + "/accounts/info/" + accountNumber,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map<String, Object> data = extractData(response);
            String createdAtStr = data.get("createdAt").toString();
            return LocalDateTime.parse(createdAtStr);

        } catch (Exception e) {
            log.warn("Could not get createdAt for account {}: {}", accountNumber, e.getMessage());
            // If we can't determine, treat as existing (safe default — no extra restriction)
            return LocalDateTime.now().minusDays(2);
        }
    }

    // ── Update balance in account service after transaction ───────
    public void updateBalance(String accountNumber,
                              BigDecimal newBalance,
                              String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtToken);

            Map<String, Object> body = Map.of(
                    "accountNumber", accountNumber,
                    "newBalance", newBalance
            );

            restTemplate.exchange(
                    accountServiceUrl + "/accounts/balance/update",
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            log.info("Balance updated for account {} → ₹{}", accountNumber, newBalance);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to update balance for {}: {}",
                      accountNumber, e.getMessage());
            throw new RuntimeException(
                    "Failed to update account balance. Transaction rolled back.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(ResponseEntity<Map> response) {
        if (response.getBody() == null)
            throw new RuntimeException("Empty response from account service.");
        return (Map<String, Object>) response.getBody().get("data");
    }
}
