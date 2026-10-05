package com.salestorm.repository;

import com.salestorm.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for Payment.
 * The findByIdempotencyKey method is the primary guard against duplicate payments.
 */
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** Returns existing payment if this idempotency key was already used */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
