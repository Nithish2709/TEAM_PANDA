# SALESTORM Reservation System

The reservation system temporarily locks inventory for a specific user to guarantee they can complete checkout.

## 1. Reservation State Machine

```text
AVAILABLE (Logical start state in Inventory)
    ↓
RESERVED
    ↓
PAYMENT_PENDING
    ↓
CONFIRMED
    ↓
SOLD (Logical end state in Inventory)
```

**Failure/Release Paths:**

```text
RESERVED / PAYMENT_PENDING
    ↓ (Payment fails)
PAYMENT_FAILED
    ↓
RELEASED (Inventory available_quantity increments)
```

```text
RESERVED / PAYMENT_PENDING
    ↓ (Timeout occurs before confirmation)
TIMEOUT
    ↓
RELEASED (Inventory available_quantity increments)
```

## 2. Reservation Rules

1. **Expiry**: Every reservation MUST have an `expires_at` timestamp (e.g., 5 minutes from creation).
2. **Release Mechanism**: A background worker periodically scans for expired reservations and transitions them to `RELEASED`. This securely releases the inventory back to the pool.
3. **Idempotency**: The same `idempotency_key` cannot create multiple reservations. If a user clicks "Buy" twice rapidly, the second request returns the first reservation.
4. **No Negative Inventory**: A reservation is ONLY created if `available_quantity > 0` during the pessimistic lock transaction.
5. **No Double Selling**: A `RELEASED` or `TIMEOUT` reservation cannot later transition to `CONFIRMED`.
6. **Atomic Confirmation**: Confirming a reservation (moving it to `CONFIRMED` and eventually `SOLD`) must be an atomic database transaction.
