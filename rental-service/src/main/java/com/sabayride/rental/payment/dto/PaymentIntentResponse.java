package com.sabayride.rental.payment.dto;

import com.sabayride.rental.booking.PaymentStatus;
import com.sabayride.rental.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID paymentId,
        UUID bookingId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String qrPayload,
        String qrImage,
        String deeplink,
        String transactionId,
        Instant expiresAt,
        Instant paidAt,
        String failureReason
) {
}
