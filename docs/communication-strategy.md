# Communication Strategy — SALESTORM

## Synchronous vs Asynchronous Decision Table

| Operation | Mode | Protocol | Why |
|---|---|---|---|
| Product page load | **Synchronous** | REST GET | Customer needs immediate data |
| Cart add/remove | **Synchronous** | REST POST/DELETE | Immediate cart state needed |
| Inventory check (pre-buy) | **Synchronous** | REST GET | Customer needs current availability |
| **Inventory Reservation** | **Synchronous** | REST POST + DB lock | Customer MUST know immediately if they secured a unit |
| **Checkout** | **Synchronous** | REST POST | Customer waits for payment result |
| **Payment Processing** | **Synchronous** | HTTPS → Payment Gateway | Blocking — gateway response needed to proceed |
| Payment Success → Order Creation | **Asynchronous** | Kafka `payment.confirmed` | Order Service can be down; event persists in broker |
| Order Confirmed → Shipment | **Asynchronous** | Kafka `order.confirmed` | Customer doesn't need to wait for warehouse |
| Order Confirmed → Notification | **Asynchronous** | Kafka `order.confirmed` | Non-critical; failure must not block order |
| Reservation Expiry Release | **Asynchronous** | Scheduler (every 60s) | Background cleanup; no user waiting |
| Order tracking updates | **Synchronous (poll)** | REST GET | Customer pulls status on demand |

---

## Design Principles

### Why Reservation is Synchronous

The inventory reservation is the **most critical synchronous operation**. The customer clicked "Buy Now" and is actively waiting. They need to know within ~1 second whether they secured one of the 100 units. Making this asynchronous would require the customer to poll — a poor user experience and complex implementation.

The pessimistic DB lock serialises concurrent reservations — at most one transaction modifies inventory at a time. This is the correctness guarantee.

### Why Payment → Order is Asynchronous

This is the critical jury question. After payment succeeds:
1. We publish `PaymentConfirmedEvent` to Kafka **before returning the response**
2. Kafka durably stores this event (7-day retention)
3. Order Service consumes the event when it is available

If Order Service crashes at this exact moment, the event waits in Kafka. When Order Service restarts, it picks up from its committed offset and processes the event. No order is lost.

This cannot be done synchronously without risking losing a successful payment.

### Why Notifications are Asynchronous

A customer whose order is confirmed should receive an email/SMS. But:
- Email provider could be down
- Notification delay of seconds is acceptable
- Notification failure must **never** roll back an order

Async with retry + DLQ gives us all of these properties.

---

## Eventual Consistency Boundaries

| Data | Consistency |
|---|---|
| Inventory available_quantity | **Strong** — DB transaction + pessimistic lock |
| Reservation status | **Strong** — same transaction |
| Payment status | **Strong** — DB write before gateway call |
| Order existence | **Eventual** — created asynchronously after payment |
| Customer notification | **Eventual** — best-effort delivery |

---

## Backpressure Strategy

Under extreme load, if Kafka consumer lag grows:
1. Consumer instances auto-scale (Kubernetes HPA)
2. Kafka topic partitions allow parallel consumption
3. If consumer is overwhelmed, messages wait in Kafka — no data loss
4. DLQ catches persistent failures after retry exhaustion
