package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SesClient sesClient;
    private final AppConfig appConfig;

    @Value("${app.from-email}")
    private String fromEmail;

    @Async
    public void sendRegistrationOtp(String toEmail, String fullName, String otp) {
        String subject = appConfig.getBankName() + " — Email Verification OTP";
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px">
              <h2 style="color:#1d4ed8">%s — Email Verification</h2>
              <p>Dear <strong>%s</strong>,</p>
              <p>Use the OTP below to verify your email:</p>
              <div style="text-align:center;margin:32px 0">
                <span style="font-size:40px;font-weight:bold;letter-spacing:14px;color:#1d4ed8;background:#eff6ff;padding:18px 32px;border-radius:10px;display:inline-block">%s</span>
              </div>
              <p> Valid for <strong>%d minutes</strong> only.</p>
              <p style="color:#ef4444"> Never share this OTP. %s will never ask for it.</p>
            </div>
            """.formatted(appConfig.getBankName(), fullName, otp,
                appConfig.getOtpExpiryMinutes(), appConfig.getBankName());
        send(toEmail, subject, body);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName,
                                 String customerId, String accountNumber,
                                 String accountType) {
        String subject = appConfig.getBankName() + " — Welcome! Your Account is Ready";
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px">
              <h2 style="color:#1d4ed8">Welcome to %s! </h2>
              <p>Dear <strong>%s</strong>, your account is ready.</p>
              <table style="width:100%%;border-collapse:collapse;margin:20px 0">
                <tr style="background:#eff6ff">
                  <td style="padding:12px;border:1px solid #bfdbfe;font-weight:bold">Customer ID</td>
                  <td style="padding:12px;border:1px solid #bfdbfe;font-size:20px;font-weight:bold;color:#1d4ed8;letter-spacing:2px">%s</td>
                </tr>
                <tr>
                  <td style="padding:12px;border:1px solid #bfdbfe;font-weight:bold">Account Number</td>
                  <td style="padding:12px;border:1px solid #bfdbfe;font-weight:bold;color:#059669">%s</td>
                </tr>
                <tr style="background:#eff6ff">
                  <td style="padding:12px;border:1px solid #bfdbfe;font-weight:bold">Account Type</td>
                  <td style="padding:12px;border:1px solid #bfdbfe">%s Account</td>
                </tr>
              </table>
              <div style="background:#fef3c7;border:1px solid #fcd34d;border-radius:8px;padding:14px">
                <p style="margin:0;color:#92400e"><strong>Next Step:</strong> Log in with your Customer ID and set your 4-digit PIN to activate the account.</p>
              </div>
            </div>
            """.formatted(appConfig.getBankName(), fullName, customerId,
                accountNumber, accountType);
        send(toEmail, subject, body);
    }

    @Async
    public void sendPinSetupOtp(String toEmail, String fullName,
                                String accountNumber, String otp) {
        String subject = appConfig.getBankName() + " — PIN Setup OTP for " + accountNumber;
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px">
              <h2 style="color:#1d4ed8">%s — Account PIN Setup</h2>
              <p>Dear <strong>%s</strong>, OTP for PIN setup on account <strong>%s</strong>:</p>
              <div style="text-align:center;margin:32px 0">
                <span style="font-size:40px;font-weight:bold;letter-spacing:14px;color:#1d4ed8;background:#eff6ff;padding:18px 32px;border-radius:10px;display:inline-block">%s</span>
              </div>
              <p> Valid for <strong>%d minutes</strong>. Do not share.</p>
            </div>
            """.formatted(appConfig.getBankName(), fullName, accountNumber,
                otp, appConfig.getOtpExpiryMinutes());
        send(toEmail, subject, body);
    }

    @Async
    public void sendNewAccountEmail(String toEmail, String fullName,
                                    String accountNumber, String accountType) {
        String subject = appConfig.getBankName() + " — New Account Added Successfully";
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px">
              <h2 style="color:#1d4ed8">New Account Added </h2>
              <p>Dear <strong>%s</strong>, a new <strong>%s</strong> account has been added:</p>
              <p style="font-size:20px;font-weight:bold;color:#1d4ed8;letter-spacing:1px">%s</p>
              <p>Set your PIN to activate it.</p>
            </div>
            """.formatted(fullName, accountType, accountNumber);
        send(toEmail, subject, body);
    }

    // Sends via HTTPS port 443 — works on ANY network including college/office
    private void send(String to, String subject, String htmlBody) {
        try {
            log.info("=== SENDING via AWS SES (HTTPS) to: {} ===", to);

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject).charset("UTF-8").build())
                            .bod   y(Body.builder()
                                    .html(Content.builder()
                                            .data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("=== EMAIL SENT  MessageId: {} ===", response.messageId());

        } catch (SesException e) {
            log.error("=== AWS SES ERROR: {} ===", e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("=== EMAIL FAILED to: {} | {} ===", to, e.getMessage());
        }
    }
}