package com.bank.accountidentityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_id", updatable = false, nullable = false)
    private UUID roleId;

    @Column(name = "role_name", nullable = false, unique = true, length = 30)
    @Enumerated(EnumType.STRING)
    private RoleName roleName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RoleName {
        CUSTOMER,
        COMPLIANCE,
        ADMIN
    }
}
