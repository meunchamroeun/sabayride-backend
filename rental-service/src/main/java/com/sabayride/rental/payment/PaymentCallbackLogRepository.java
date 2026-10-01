package com.sabayride.rental.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLog, Long> {

    boolean existsByTransactionIdAndOutcome(String transactionId, String outcome);

    Optional<PaymentCallbackLog> findTopByTransactionIdOrderByReceivedAtDesc(String transactionId);
}
