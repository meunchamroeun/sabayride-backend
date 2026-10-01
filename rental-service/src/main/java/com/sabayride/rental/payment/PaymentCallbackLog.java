package com.sabayride.rental.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "payment_callback_log")
public class PaymentCallbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 120)
    private String transactionId;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "outcome", nullable = false, length = 60)
    private String outcome;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentCallbackLog() {
    }

    public PaymentCallbackLog(String transactionId, boolean signatureValid, String rawPayload, String outcome) {
        this.transactionId = transactionId;
        this.signatureValid = signatureValid;
        this.rawPayload = rawPayload;
        this.outcome = outcome;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
