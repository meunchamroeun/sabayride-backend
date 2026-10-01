package com.sabayride.rental.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabayride.rental.booking.*;
import com.sabayride.rental.common.ApiException;
import com.sabayride.rental.payment.dto.InitiatePaymentRequest;
import com.sabayride.rental.payment.dto.PaymentIntentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BookingRepository bookings;

    @Mock
    private BookingItemRepository items;

    @Mock
    private PaymentRepository payments;

    @Mock
    private PaymentCallbackLogRepository callbackLogs;

    @Mock
    private PayWayClient payWayClient;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                bookings, items, payments, callbackLogs, payWayClient, new ObjectMapper()
        );
    }

    @Test
    void initiatePayment_rejectsPayAtShop() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.create(
                UUID.randomUUID(), "Jane Doe", UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                UUID.randomUUID(), PickupMethod.SHOP_PICKUP, PaymentOption.PAY_AT_SHOP,
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(0.10), Instant.now().plusSeconds(3600)
        );

        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class, () ->
                paymentService.initiatePayment(bookingId, new InitiatePaymentRequest(PaymentMethod.KHQR))
        );
        assertEquals("NOT_PAYABLE_ONLINE", ex.getCode());
    }

    @Test
    void initiatePayment_rejectsAlreadyPaid() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.create(
                UUID.randomUUID(), "Jane Doe", UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                UUID.randomUUID(), PickupMethod.SHOP_PICKUP, PaymentOption.PAY_NOW,
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(0.10), Instant.now().plusSeconds(3600)
        );
        booking.setPaymentStatus(PaymentStatus.PAID);

        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class, () ->
                paymentService.initiatePayment(bookingId, new InitiatePaymentRequest(PaymentMethod.KHQR))
        );
        assertEquals("ALREADY_PAID", ex.getCode());
    }

    @Test
    void initiatePayment_success() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.create(
                UUID.randomUUID(), "Jane Doe", UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                UUID.randomUUID(), PickupMethod.SHOP_PICKUP, PaymentOption.PAY_NOW,
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(0.10), Instant.now().plusSeconds(3600)
        );

        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(items.findByBookingId(bookingId)).thenReturn(Optional.empty());

        PayWayClient.PurchaseResult fakeResult = new PayWayClient.PurchaseResult(
                "SR1234567890",
                "000201010212...samplekhqr",
                "data:image/png;base64,sample...",
                "abamobilebank://ababank.com?type=payway&qrcode=000201...",
                "00",
                "Success!"
        );
        when(payWayClient.createPurchase(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(fakeResult);

        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentResponse response = paymentService.initiatePayment(
                bookingId, new InitiatePaymentRequest(PaymentMethod.KHQR)
        );

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.status());
        assertEquals(PaymentMethod.KHQR, response.method());
        assertEquals("000201010212...samplekhqr", response.qrPayload());
        assertEquals("abamobilebank://ababank.com?type=payway&qrcode=000201...", response.deeplink());
        verify(payments).save(any(Payment.class));
    }

    @Test
    void getPaymentStatus_confirmsBookingWhenPaid() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.create(
                UUID.randomUUID(), "Jane Doe", UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                UUID.randomUUID(), PickupMethod.SHOP_PICKUP, PaymentOption.PAY_NOW,
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(0.10), Instant.now().plusSeconds(3600)
        );

        Payment payment = Payment.forBooking(
                bookingId, BigDecimal.valueOf(30), "USD", PaymentMethod.KHQR,
                "SR1234567890", "000201...", Instant.now().plusSeconds(3600)
        );

        when(payments.findLatestByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(payWayClient.checkTransaction("SR1234567890")).thenReturn(
                new PayWayClient.CheckTransactionResult("SR1234567890", "APPROVED", 0, BigDecimal.valueOf(30), "{}")
        );
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));

        PaymentIntentResponse response = paymentService.getPaymentStatus(bookingId);

        assertEquals(PaymentStatus.PAID, response.status());
        assertEquals(PaymentStatus.PAID, booking.getPaymentStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertNull(booking.getHoldExpiresAt());
    }
}
