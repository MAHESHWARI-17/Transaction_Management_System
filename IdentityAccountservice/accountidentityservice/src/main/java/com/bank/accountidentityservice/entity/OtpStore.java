package com.bank.accountidentityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_store")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpStore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "otp_id", updatable = false, nullable = false)
    private UUID otpId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "otp_hash", nullable = false, columnDefinition = "TEXT")
    private String otpHash;

    @Column(name = "purpose", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    @Column(name = "ref_value", length = 150)
    private String refValue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isValid() {
        return usedAt == null && LocalDateTime.now().isBefore(expiresAt);
    }

    public enum OtpPurpose {
        REGISTRATION,
        PIN_SETUP
    }
}
