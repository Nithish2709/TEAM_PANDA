# ADR-002 — Synchronous vs Asynchronous Communication

**Status:** Accepted  
**Date:** 2026-10-05  
**Deciders:** TeamPanda

---

## Context

The purchase pipeline spans multiple services: Inventory → Checkout → Payment → Order → Shipment → Notification. We must decide for each interaction: does the caller wait for the result (synchronous), or fire and forget via a message broker (asynchronous)?

The trade-off is between:
- **Synchronous**: Simpler, gives immediate feedback, but couples services — if downstream fails, the caller fails too.
- **Asynchronous**: Decouples services, improves resilience and throughput, but delays feedback and introduces eventual consistency.

---

## Decision

We apply a **hybrid strategy** based on what the customer needs immediately vs. what can happen in the background.

| Interaction | Mode | Reason |
|---|---|---|
| Buy Request → Inventory Reservation | **Synchronous** | Customer must know immediately if they got a unit |
| Checkout → Payment | **Synchronous** | Customer waits for payment confirmation |
| Payment Success → Order Creation | **Asynchronous (Kafka)** | Order Service can be down; event persists in broker |
| Order Confirmed → Shipment | **Asynchronous** | Customer doesn't wait for fulfilment to begin |
| Order → Notification | **Asynchronous** | Non-critical; notification failure must not fail the order |
| Reservation Expiry Release | **Asynchronous (Scheduler)** | Background sweep; no user is waiting |

---

## Alternatives Considered

1. **Everything synchronous (pure REST)**: Simple but creates a fragile chain — one service down kills the entire flow. Payment success followed by Order Service crash = lost order.
2. **Everything asynchronous**: Eventual consistency for inventory reservation is unacceptable — customer can't know if they got the unit.
3. **Saga pattern with choreography**: Considered but adds significant complexity for a prototype. The hybrid approach gives the same guarantees with less overhead.

---

## Advantages

- Customer gets an immediate reservation response (synchronous where it matters)
- Payment success event survives Order Service crashes (Kafka persists it)
- Notification failures are isolated — they never affect the critical payment path
- Services can scale independently

## Disadvantages

- Two different communication patterns increase conceptual surface area
- Asynchronous flows are harder to trace end-to-end (mitigated by correlation IDs / OpenTelemetry)
- Eventual consistency means the order may appear delayed to the customer

## Consequences

- `PaymentConfirmedEvent` must be produced to Kafka on every successful payment
- Order Service must be an idempotent Kafka consumer
- Notification Service must not block the order pipeline
- Distributed tracing (OpenTelemetry) is essential to trace across the async boundary
