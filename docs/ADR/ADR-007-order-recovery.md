# ADR-007 — Order Recovery After Payment Success + Order Service Failure

**Status:** Accepted  
**Date:** 2026-10-05  
**Deciders:** TeamPanda

---

## Context

This is one of the most critical failure scenarios for the jury:

> Payment succeeds. The payment gateway returns 200. But before we can create the Order record, the Order Service crashes (OOM kill, network partition, pod restart).

The customer was charged. If we do nothing, the money is taken but no order exists.

---

## The Problem with Synchronous Order Creation

```
Payment Success (200)
        ↓
[CRASH HERE]
        ↓
Order Creation — NEVER CALLED
```

If we call Order Service synchronously after payment, a crash in Order Service = lost order. We cannot simply retry because we don't know if Order Service received and processed the first call.

---

## Decision

We use an **event-driven recovery pattern via Kafka**:

```
Payment Service:
  1. Mark payment as SUCCESS in DB
  2. Publish PaymentConfirmedEvent to Kafka topic 'payment.confirmed'
  3. Return 201 to customer

Order Service (Kafka Consumer):
  1. Consume 'payment.confirmed' event
  2. Check idempotency key → if order already exists, skip (idempotent consumer)
  3. Create order
  4. Publish 'order.confirmed' to Kafka
```

### Why this works during a crash

Kafka retains the `PaymentConfirmedEvent` until the Order Service consumer group acknowledges it. If Order Service crashes:
- The event stays in Kafka (configured `retention.ms` = 7 days)
- When Order Service restarts, it resumes from its last committed offset
- The event is reprocessed — idempotency key prevents duplicate order

### Dead Letter Queue

If processing fails repeatedly (e.g., corrupted event, schema mismatch):

```
Retry 1 (immediate)
Retry 2 (5s backoff)
Retry 3 (30s backoff)
→ Move to 'payment.confirmed.dlq'
→ Alert on-call engineer
→ Manual replay after fix
```

---

## Prototype Implementation

In the prototype, Order Service is called synchronously by CheckoutService **but** the FailureSimulator can inject `ORDER_SERVICE_CRASH` to demonstrate the failure path. The architecture document shows the Kafka-based recovery that would be used in production.

---

## Alternatives Considered

| Approach | Problem |
|---|---|
| Synchronous REST retry | No durability — crash between payment and retry = lost order |
| Two-phase commit (2PC) | Extremely complex, poor performance, tight coupling |
| Saga with compensating transactions | Valid but complex; adds rollback logic throughout |
| **Kafka event + idempotent consumer** | **Selected — durable, simple, battle-proven** |

## Consequences

- Payment Service MUST publish `PaymentConfirmedEvent` before returning 201
- Order Service MUST check idempotency key before creating an order
- `payment.confirmed.dlq` topic must be monitored with alerts
- Recovery from DLQ must be a documented runbook
