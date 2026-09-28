package com.sabayride.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to identity.otp_code.
 * Stores hashed OTP codes for phone verification and password resets.
 */
@Entity
@Table(name = "otp_code")
@Getter
@Setter
@NoArgsConstructor
public class OtpCode {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "purpose", nullable = false, length = 30)
    private String purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
