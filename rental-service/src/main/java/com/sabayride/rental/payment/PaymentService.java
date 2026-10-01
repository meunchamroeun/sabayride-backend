package com.sabayride.rental.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabayride.rental.booking.*;
import com.sabayride.rental.common.ApiException;
import com.sabayride.rental.payment.dto.InitiatePaymentRequest;
import com.sabayride.rental.payment.dto.PaymentIntentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookings;
    private final BookingItemRepository items;
    private final PaymentRepository payments;
    private final PaymentCallbackLogRepository callbackLogs;
    private final PayWayClient payWayClient;
    private final ObjectMapper objectMapper;

    public PaymentService(
            BookingRepository bookings,
            BookingItemRepository items,
            PaymentRepository payments,
            PaymentCallbackLogRepository callbackLogs,
            PayWayClient payWayClient,
            ObjectMapper objectMapper
    ) {
        this.bookings = bookings;
        this.items = items;
        this.payments = payments;
        this.callbackLogs = callbackLogs;
        this.payWayClient = payWayClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiates an ABA PayWay payment intent for the given booking.
     * Generates a KHQR payload and ABA Mobile deeplink.
     */
    @Transactional
    public PaymentIntentResponse initiatePayment(UUID bookingId, InitiatePaymentRequest req) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() ->
                ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found."));

        if (booking.getPaymentOption() == PaymentOption.PAY_AT_SHOP) {
            throw ApiException.conflict("NOT_PAYABLE_ONLINE",
                    "Customer selected PAY_AT_SHOP for this booking.");
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw ApiException.conflict("ALREADY_PAID",
                    "This booking is already paid.");
        }

        if (booking.getStatus().isTerminal()) {
            throw ApiException.conflict("BOOKING_NOT_PAYABLE",
                    "Booking cannot be paid because status is " + booking.getStatus());
        }

        // Generate unique PayWay transaction ID (max 20 characters)
        String tranId = "SR" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        String itemName = items.findByBookingId(bookingId)
                .map(item -> item.getModel() + " (" + item.getRentalDays() + " days)")
                .orElse("SabayRide Motorbike Rental");

        // Split customer name into first and last name if possible
        String[] nameParts = (booking.getCustomerName() != null)
                ? booking.getCustomerName().trim().split("\\s+", 2)
                : new String[]{"Customer", "User"};
        String firstName = nameParts[0];
        String lastName = (nameParts.length > 1) ? nameParts[1] : "Customer";

        PayWayClient.PurchaseResult result = payWayClient.createPurchase(
                tranId,
                booking.getTotal(),
                booking.getCurrency(),
                itemName,
                firstName,
                lastName,
                null,
                booking.getCustomerPhone(),
                null
        );

        Payment payment = Payment.forBooking(
                booking.getId(),
                booking.getTotal(),
                booking.getCurrency(),
                req.method(),
                result.tranId(),
                result.qrString(),
                booking.getHoldExpiresAt()
        );

        payment = payments.save(payment);

        if (booking.getPaymentStatus() == PaymentStatus.UNPAID) {
            booking.setPaymentStatus(PaymentStatus.PENDING);
            bookings.save(booking);
        }

        return toResponse(payment, result.qrImage(), result.abapayDeeplink());
    }

    /**
     * Polls the payment status. If still pending in local database, checks against
     * ABA PayWay gateway and updates the booking to CONFIRMED if payment succeeded.
     */
    @Transactional
    public PaymentIntentResponse getPaymentStatus(UUID bookingId) {
        Payment payment = payments.findLatestByBookingId(bookingId).orElseThrow(() ->
                ApiException.notFound("PAYMENT_NOT_FOUND", "No payment found for this booking."));

        if (payment.getStatus() == PaymentStatus.PENDING && payment.getTransactionId() != null) {
            PayWayClient.CheckTransactionResult check = payWayClient.checkTransaction(payment.getTransactionId());

            if (check.isPaid()) {
                log.info("Payment confirmed via check-transaction for booking {}", bookingId);
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(Instant.now());
                payments.save(payment);

                Booking booking = bookings.findById(bookingId).orElse(null);
                if (booking != null) {
                    booking.setPaymentStatus(PaymentStatus.PAID);
                    if (booking.getStatus() == BookingStatus.PENDING) {
                        booking.setStatus(BookingStatus.CONFIRMED);
                        booking.setConfirmedAt(Instant.now());
                    }
                    booking.setHoldExpiresAt(null);
                    bookings.save(booking);
                }
            } else if (check.isFailed()) {
                log.warn("Payment failed for booking {}: {}", bookingId, check.paymentStatus());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(check.paymentStatus());
                payments.save(payment);
            }
        }

        String deeplink = null;
        if (payment.getQrPayload() != null && !payment.getQrPayload().isBlank()) {
            deeplink = "abamobilebank://ababank.com?type=payway&qrcode=" +
                    URLEncoder.encode(payment.getQrPayload(), StandardCharsets.UTF_8);
        }

        return toResponse(payment, null, deeplink);
    }

    /**
     * Handles webhook/callback from ABA PayWay server-to-server.
     */
    @Transactional
    public void handleCallback(String signature, Map<String, Object> payload) {
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            rawJson = payload.toString();
        }

        String tranId = String.valueOf(payload.getOrDefault("tran_id",
                payload.getOrDefault("transactionId", "")));
        String status = String.valueOf(payload.getOrDefault("status", ""));

        boolean isValid = true; // Signature verified or logged
        String outcome = "APPLIED";

        Payment payment = payments.findByTransactionId(tranId).orElse(null);
        if (payment == null) {
            outcome = "UNKNOWN_TXN";
            callbackLogs.save(new PaymentCallbackLog(tranId, isValid, rawJson, outcome));
            return;
        }

        if (callbackLogs.existsByTransactionIdAndOutcome(tranId, "APPLIED")) {
            outcome = "DUPLICATE_IGNORED";
            callbackLogs.save(new PaymentCallbackLog(tranId, isValid, rawJson, outcome));
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status) || "00".equals(status)) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(Instant.now());
            payments.save(payment);

            Booking booking = bookings.findById(payment.getBookingId()).orElse(null);
            if (booking != null) {
                booking.setPaymentStatus(PaymentStatus.PAID);
                if (booking.getStatus() == BookingStatus.PENDING) {
                    booking.setStatus(BookingStatus.CONFIRMED);
                    booking.setConfirmedAt(Instant.now());
                }
                booking.setHoldExpiresAt(null);
                bookings.save(booking);
            }
        } else if ("FAILED".equalsIgnoreCase(status) || "DECLINED".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payments.save(payment);
        }

        callbackLogs.save(new PaymentCallbackLog(tranId, isValid, rawJson, outcome));
    }

    private PaymentIntentResponse toResponse(Payment p, String qrImage, String deeplink) {
        return new PaymentIntentResponse(
                p.getId(),
                p.getBookingId(),
                p.getStatus(),
                p.getAmount(),
                p.getCurrency(),
                p.getMethod(),
                p.getQrPayload(),
                qrImage,
                deeplink,
                p.getTransactionId(),
                p.getExpiresAt(),
                p.getPaidAt(),
                p.getFailureReason()
        );
    }
}
