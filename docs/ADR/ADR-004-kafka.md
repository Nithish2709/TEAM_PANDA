# ADR-004 — Message Broker: Kafka

**Status:** Accepted  
**Date:** 2026-10-05  
**Deciders:** TeamPanda

---

## Context

The SALESTORM system includes critical async flows:
- Payment success must trigger order creation even if Order Service is temporarily down
- Failed messages must be retried, then dead-lettered for manual inspection
- Events may be delivered at-least-once; consumers must be idempotent

We need a message broker capable of handling burst traffic and guaranteeing durability.

---

## Decision

We chose **Apache Kafka**.

---

## Alternatives Considered

| Option | Pros | Cons |
|---|---|---|
| **Kafka** | Durable, ordered, replay, high throughput | Needs Zookeeper, operationally complex |
| **RabbitMQ** | Simple, flexible routing, good DLQ support | No log-based replay, lower throughput ceiling |
| **Amazon SQS** | Managed, simple | Vendor lock-in, not self-hosted |
| **Direct REST calls** | Simple | No durability — Order Service crash = lost data |

---

## Key Events

| Topic | Producer | Consumer |
|---|---|---|
| `payment.confirmed` | Payment Service | Order Service |
| `payment.failed` | Payment Service | Inventory Service (release) |
| `reservation.expired` | Scheduler | Inventory Service |
| `order.confirmed` | Order Service | Shipment Service, Notification |
| `order.shipped` | Shipment Service | Notification Service |

---

## Advantages

- **Durability**: Events persist in the log — Order Service can crash and recover, consuming from where it stopped
- **Replay**: Can re-process events for debugging or recovery
- **Ordering**: Per-partition ordering ensures events for one order are processed in sequence
- **Decoupling**: Order Service and Payment Service don't need to be up simultaneously

## Disadvantages

- Requires Zookeeper (or KRaft in newer versions) — operational overhead
- At-least-once delivery requires idempotent consumers
- Higher operational complexity vs. RabbitMQ for small teams

## Consequences

- All Kafka consumers must be idempotent (check event ID before processing)
- Dead-letter topics (`*.dlq`) must be monitored for alerts
- In the prototype, Kafka is optional — the CheckoutService calls OrderService synchronously; the architecture shows how the Kafka path would work
- Consumer group IDs must be unique per service to avoid multiple services consuming the same event
