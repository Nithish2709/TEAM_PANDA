package com.salestorm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a payment attempt tied to an order.
 * The idempotencyKey ensures the same payment is never processed twice,
 * even under retries or double-clicks.
 */
@Entity
@Table(name = "payment", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = "idempotency_key")
})
public class Payment {

    public enum Status {
        PENDING, SUCCESS, FAILED, TIMEOUT, DUPLICATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;

    @Column(nullable = false)
    private String orderId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** External transaction reference from the payment gateway */
    private String transactionReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** e.g. STRIPE, RAZORPAY, MOCK */
    private String provider;

    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Payment() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
