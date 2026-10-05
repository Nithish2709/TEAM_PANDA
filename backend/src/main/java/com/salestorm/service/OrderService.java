package com.salestorm.service;

import com.salestorm.domain.Order;
import com.salestorm.domain.Payment;
import com.salestorm.repository.OrderRepository;
import com.salestorm.simulator.FailureSimulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Order service implementing:
 *  - Idempotency: same idempotency key returns existing order
 *  - State machine: strict transition guards via Order.transitionTo()
 *  - Order Service crash simulation for jury demo
 *
 * In the full architecture, OrderService is an event consumer:
 *   PaymentConfirmed (Kafka) → OrderService creates order
 * In the prototype, it is called synchronously by CheckoutService.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FailureSimulator failureSimulator;

    public OrderService(OrderRepository orderRepository, FailureSimulator failureSimulator) {
        this.orderRepository = orderRepository;
        this.failureSimulator = failureSimulator;
    }

    // ── Create Order ──────────────────────────────────────────────────────────

    /**
     * Creates a new order after successful payment.
     * Idempotent: if called twice with the same key, returns existing order.
     *
     * JURY SCENARIO: If ORDER_SERVICE_CRASH is injected, this throws.
     * In the real system, the PaymentConfirmed Kafka event remains in the broker
     * and is retried when the Order Service recovers.
     */
    @Transactional
    public Order createOrder(Long customerId, Long productId, Integer quantity,
                             BigDecimal totalAmount, String reservationId,
                             String idempotencyKey) {

        // Simulate Order Service crash (for jury demo)
        failureSimulator.checkAndSimulate(FailureSimulator.FailureType.ORDER_SERVICE_CRASH);

        // Idempotency check
        Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setReservationId(reservationId);
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(Order.Status.CREATED);

        return orderRepository.save(order);
    }

    // ── State Transitions ─────────────────────────────────────────────────────

    @Transactional
    public Order advanceState(String orderId, Order.Status nextStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.transitionTo(nextStatus); // throws if transition is illegal
        return orderRepository.save(order);
    }

    @Transactional
    public Order confirmOrder(String orderId) {
        return advanceState(orderId, Order.Status.CONFIRMED);
    }

    @Transactional
    public Order cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!order.canTransitionTo(Order.Status.CANCELLED)) {
            throw new IllegalStateException("Cannot cancel order in state: " + order.getStatus());
        }
        order.setStatus(Order.Status.CANCELLED);
        return orderRepository.save(order);
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    // ── Payment outcome handler ────────────────────────────────────────────────

    /**
     * Called by CheckoutService after payment result is known.
     * Handles both SUCCESS and FAILED outcomes.
     */
    @Transactional
    public void handlePaymentOutcome(String orderId, Payment.Status paymentStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (paymentStatus == Payment.Status.SUCCESS) {
            if (order.canTransitionTo(Order.Status.CONFIRMED)) {
                order.transitionTo(Order.Status.CONFIRMED);
            }
        } else {
            if (order.canTransitionTo(Order.Status.PAYMENT_FAILED)) {
                order.transitionTo(Order.Status.PAYMENT_FAILED);
            }
        }
        orderRepository.save(order);
    }
}
