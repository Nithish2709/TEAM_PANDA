package com.salestorm.service;

import com.salestorm.domain.Payment;
import com.salestorm.repository.PaymentRepository;
import com.salestorm.simulator.FailureSimulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment service implementing:
 *  - Idempotency: same idempotency key returns existing payment, no double-charge
 *  - Failure simulation: PAYMENT_FAILURE and PAYMENT_TIMEOUT injectable
 *  - Status tracking: PENDING → SUCCESS | FAILED | TIMEOUT
 *
 * In the full architecture this would call a real payment gateway (Stripe/Razorpay).
 * For the prototype it uses the FailureSimulator to demonstrate all failure paths.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FailureSimulator failureSimulator;

    public PaymentService(PaymentRepository paymentRepository, FailureSimulator failureSimulator) {
        this.paymentRepository = paymentRepository;
        this.failureSimulator = failureSimulator;
    }

    // ── Process Payment ───────────────────────────────────────────────────────

    /**
     * Processes a payment for an order.
     * If the idempotency key already exists, returns the cached result — no retry risk.
     *
     * @param orderId        the order being paid
     * @param amount         amount in INR/USD
     * @param idempotencyKey unique key for this payment attempt (from client)
     * @return the Payment record (may be existing if duplicate request)
     */
    @Transactional
    public Payment processPayment(String orderId, BigDecimal amount, String idempotencyKey) {

        // 1. Idempotency check
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Payment cached = existing.get();
            cached.setStatus(Payment.Status.DUPLICATE);
            return cached; // Return previous result, no gateway call
        }

        // 2. Create a PENDING payment record BEFORE calling the gateway
        //    This ensures we can reconcile even if the gateway response is lost
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProvider("MOCK_GATEWAY");
        payment.setTransactionReference("TXN-" + UUID.randomUUID());
        payment.setStatus(Payment.Status.PENDING);
        payment = paymentRepository.save(payment);

        // 3. Call gateway (simulated) — may throw SimulatedFailureException
        try {
            failureSimulator.checkAndSimulate(FailureSimulator.FailureType.PAYMENT_FAILURE);
            failureSimulator.checkAndSimulate(FailureSimulator.FailureType.PAYMENT_TIMEOUT);

            // 4a. Gateway success
            payment.setStatus(Payment.Status.SUCCESS);

        } catch (FailureSimulator.SimulatedFailureException e) {
            // 4b. Gateway failure / timeout
            boolean isTimeout = e.getMessage().contains("Timeout");
            payment.setStatus(isTimeout ? Payment.Status.TIMEOUT : Payment.Status.FAILED);
            payment.setFailureReason(e.getMessage());
        }

        return paymentRepository.save(payment);
    }

    public Optional<Payment> getPayment(String paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
