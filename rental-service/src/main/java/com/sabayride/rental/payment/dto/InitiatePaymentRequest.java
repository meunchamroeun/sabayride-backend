package com.sabayride.rental.payment.dto;

import com.sabayride.rental.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod method
) {
}
