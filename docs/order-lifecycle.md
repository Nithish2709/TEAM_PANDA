# SALESTORM Order Lifecycle

The Order Service manages the final state of the customer's purchase after inventory has been secured and payment confirmed.

## 1. Order State Machine

```text
CREATED (Initial state, linked to reservation)
    ↓
PAYMENT_PENDING (Waiting for payment gateway)
    ↓
CONFIRMED (Payment successful)
    ↓
PROCESSING (Warehouse operations)
    ↓
SHIPPED (Handed to courier)
    ↓
OUT_FOR_DELIVERY
    ↓
DELIVERED
```

**Failure Paths:**
```text
PAYMENT_PENDING → CANCELLED (Due to payment failure or timeout)
```

## 2. Order Creation Invariants

- **Idempotency**: An order is created based on a confirmed payment. The `payment_id` acts as a unique constraint. If the message broker delivers the `PaymentSucceeded` event twice, the database will reject the second attempt to create an order for the same `payment_id`.
- **Linking**: Every order MUST map exactly to one valid, `CONFIRMED` reservation.
- **State Protection**: Invalid transitions (e.g., moving from `DELIVERED` back to `PAYMENT_PENDING`) are strictly rejected by the application logic.
