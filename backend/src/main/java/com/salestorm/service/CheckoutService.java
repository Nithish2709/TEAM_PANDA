package com.salestorm.service;

import com.salestorm.domain.InventoryReservation;
import com.salestorm.domain.Order;
import com.salestorm.domain.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * CheckoutFacade orchestrates the purchase workflow.
 *
 * It does NOT own business logic — it delegates to specialized services.
 * Pattern: Facade (GoF) — simplifies the complex multi-step purchase flow
 * for callers (controller), while keeping each service focused on its domain.
 *
 * Checkout flow:
 *   1. Reserve inventory      (InventoryService)
 *   2. Create order stub      (OrderService)
 *   3. Initiate payment       (PaymentService)
 *   4. Handle outcome:
 *      a. SUCCESS → confirm reservation + confirm order
 *      b. FAILURE → release reservation + mark order PAYMENT_FAILED
 */
@Service
public class CheckoutService {

    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderService orderService;

    public CheckoutService(InventoryService inventoryService,
                           PaymentService paymentService,
                           OrderService orderService) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    /**
     * Executes the full purchase pipeline.
     *
     * @param customerId       who is buying
     * @param productId        what they are buying
     * @param quantity         how many
     * @param unitPrice        price per unit
     * @param idempotencyKey   client-supplied unique key for the entire checkout attempt
     * @return CheckoutResult  contains all created entities and final status
     */
    public CheckoutResult checkout(Long customerId, Long productId, Integer quantity,
                                   BigDecimal unitPrice, String idempotencyKey) {

        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // ── Step 1: Reserve inventory (synchronous — customer needs immediate result) ──
        InventoryReservation reservation;
        try {
            reservation = inventoryService.reserveInventory(
                    productId, customerId, quantity, "RES-" + idempotencyKey);
        } catch (InventoryService.SoldOutException e) {
            return CheckoutResult.soldOut(e.getMessage());
        }

        // ── Step 2: Create order stub ─────────────────────────────────────────
        Order order;
        try {
            order = orderService.createOrder(
                    customerId, productId, quantity, totalAmount,
                    reservation.getReservationId(), "ORD-" + idempotencyKey);
            order = orderService.advanceState(order.getOrderId(), Order.Status.PAYMENT_PENDING);
        } catch (Exception e) {
            // Order service failed — release reservation to prevent stuck inventory
            inventoryService.releaseReservation(reservation.getReservationId());
            return CheckoutResult.error("Order creation failed: " + e.getMessage());
        }

        // ── Step 3: Process payment ───────────────────────────────────────────
        Payment payment = paymentService.processPayment(
                order.getOrderId(), totalAmount, "PAY-" + idempotencyKey);

        // ── Step 4: Handle payment outcome ────────────────────────────────────
        if (payment.getStatus() == Payment.Status.SUCCESS
                || payment.getStatus() == Payment.Status.DUPLICATE) {
            // Success path: move reserved → sold, confirm order
            inventoryService.confirmReservation(reservation.getReservationId());
            orderService.handlePaymentOutcome(order.getOrderId(), Payment.Status.SUCCESS);
            return CheckoutResult.success(reservation, order, payment);
        } else {
            // Failure path: release inventory, mark order failed
            inventoryService.releaseReservation(reservation.getReservationId());
            orderService.handlePaymentOutcome(order.getOrderId(), payment.getStatus());
            return CheckoutResult.paymentFailed(reservation, order, payment);
        }
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    public static class CheckoutResult {
        private final String outcome;
        private final String message;
        private final InventoryReservation reservation;
        private final Order order;
        private final Payment payment;

        public CheckoutResult(String outcome, String message, InventoryReservation reservation,
                              Order order, Payment payment) {
            this.outcome = outcome;
            this.message = message;
            this.reservation = reservation;
            this.order = order;
            this.payment = payment;
        }

        public String outcome() { return outcome; }
        public String message() { return message; }
        public InventoryReservation reservation() { return reservation; }
        public Order order() { return order; }
        public Payment payment() { return payment; }

        static CheckoutResult success(InventoryReservation r, Order o, Payment p) {
            return new CheckoutResult("SUCCESS", "Purchase complete", r, o, p);
        }
        static CheckoutResult soldOut(String msg) {
            return new CheckoutResult("SOLD_OUT", msg, null, null, null);
        }
        static CheckoutResult paymentFailed(InventoryReservation r, Order o, Payment p) {
            return new CheckoutResult("PAYMENT_FAILED", "Payment failed — inventory released", r, o, p);
        }
        static CheckoutResult error(String msg) {
            return new CheckoutResult("ERROR", msg, null, null, null);
        }
    }
}
