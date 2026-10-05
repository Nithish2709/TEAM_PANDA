package com.salestorm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a customer order.
 * Order is only created AFTER payment succeeds (via async Kafka event in full arch).
 * In the prototype, it is created synchronously after payment confirmation.
 *
 * State machine:
 *   CREATED → PAYMENT_PENDING → CONFIRMED → PROCESSING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED
 *   CREATED → CANCELLED
 *   PAYMENT_PENDING → PAYMENT_FAILED → CANCELLED
 */
@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        CREATED,
        PAYMENT_PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        OUT_FOR_DELIVERY,
        DELIVERED,
        PAYMENT_FAILED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Links back to the inventory reservation */
    private String reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.CREATED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Validates a state transition. Prevents illegal transitions like DELIVERED → PAYMENT_PENDING.
     */
    public boolean canTransitionTo(Status next) {
        return switch (this.status) {
            case CREATED          -> next == Status.PAYMENT_PENDING || next == Status.CANCELLED;
            case PAYMENT_PENDING  -> next == Status.CONFIRMED || next == Status.PAYMENT_FAILED;
            case PAYMENT_FAILED   -> next == Status.CANCELLED;
            case CONFIRMED        -> next == Status.PROCESSING;
            case PROCESSING       -> next == Status.SHIPPED;
            case SHIPPED          -> next == Status.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == Status.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public void transitionTo(Status next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException(
                String.format("Invalid order transition: %s → %s", this.status, next));
        }
        this.status = next;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
