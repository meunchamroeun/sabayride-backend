package com.sabayride.rental.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    default Optional<Payment> findLatestByBookingId(UUID bookingId) {
        List<Payment> list = findByBookingIdOrderByCreatedAtDesc(bookingId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
