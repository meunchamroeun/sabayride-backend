package com.sabayride.rental.payment;

import com.sabayride.rental.payment.dto.InitiatePaymentRequest;
import com.sabayride.rental.payment.dto.PaymentIntentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Start payment for a booking. Returns KHQR string, deeplink, and expiry.
     */
    @PostMapping("/api/v1/bookings/{bookingId}/payment")
    public ResponseEntity<PaymentIntentResponse> initiatePayment(
            @PathVariable UUID bookingId,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        PaymentIntentResponse intent = paymentService.initiatePayment(bookingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(intent);
    }

    /**
     * Poll payment status for a booking. Checks ABA PayWay gateway if pending.
     */
    @GetMapping("/api/v1/bookings/{bookingId}/payment")
    public ResponseEntity<PaymentIntentResponse> getPaymentStatus(@PathVariable UUID bookingId) {
        PaymentIntentResponse intent = paymentService.getPaymentStatus(bookingId);
        return ResponseEntity.ok(intent);
    }

    /**
     * Provider payment callback from ABA PayWay (server-to-server webhook).
     */
    @PostMapping("/api/v1/payments/callback")
    public ResponseEntity<Map<String, String>> paymentCallback(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload
    ) {
        paymentService.handleCallback(signature, payload);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Callback recorded"));
    }
}
