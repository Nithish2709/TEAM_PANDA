package com.salestorm.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a temporary hold on inventory for a customer.
 *
 * State machine:
 *   RESERVED → PAYMENT_PENDING → CONFIRMED → SOLD
 *   RESERVED → PAYMENT_FAILED  → RELEASED
 *   RESERVED → TIMEOUT         → RELEASED
 *
 * The idempotencyKey field has a unique constraint so that
 * duplicate Buy requests cannot create two reservations.
 */
@Entity
@Table(name = "inventory_reservation", uniqueConstraints = {
    @UniqueConstraint(name = "uk_reservation_idempotency_key", columnNames = "idempotency_key")
})
public class InventoryReservation {

    public enum Status {
        RESERVED, PAYMENT_PENDING, CONFIRMED, SOLD,
        PAYMENT_FAILED, TIMEOUT, RELEASED
    }

    @Id
    private String reservationId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InventoryReservation() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.RESERVED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
