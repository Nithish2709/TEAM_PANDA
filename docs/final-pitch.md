# 5-Minute Pitch — SALESTORM | SYSCRAFTERS 2026

**TeamPanda — System Design Hackathon Presentation Script**

---

## ⏱ 00:00 – 00:30 — The Problem

> "Imagine it's midnight. 10,000 customers simultaneously click Buy Now for a product with only 100 units remaining. If our system processes even one extra request, we oversell. If it crashes under load, the company loses money and trust. If the payment succeeds but the order fails, a customer was charged for nothing."

> "These are not theoretical problems. Every major e-commerce platform has experienced them. SALESTORM is our solution."

---

## ⏱ 00:30 – 01:00 — Requirements

> "The system must guarantee three hard invariants — non-negotiable:
> 1. Successful sales ≤ 100. Never oversell.
> 2. Inventory never goes negative.
> 3. A successful payment is never lost — even if our Order Service crashes."

> "Beyond correctness, the system must handle 10,000 concurrent requests, 5% payment failures, 2% duplicate requests, and a 30-second Order Service outage — all simultaneously."

---

## ⏱ 01:00 – 02:00 — High-Level Architecture

> "Our architecture is layered and purposeful. From the top:"

> "Users hit a CDN and WAF — absorbing static traffic and blocking abuse before it reaches our servers."

> "A load balancer distributes across stateless Spring Boot instances. Stateless means we can add instances horizontally without coordination."

> "API Gateway enforces rate limiting — using Redis sliding-window counters — so no single customer can hammer the system."

> "Core services: Inventory, Checkout, Payment, Order. Each has a single responsibility. They communicate synchronously where the customer needs immediate results, and via Kafka for everything that can happen asynchronously."

> "PostgreSQL is our source of truth. Redis caches product stock for display. Kafka durably persists events across service failures."

---

## ⏱ 02:00 – 03:00 — The Critical Scenario: 10,000 vs 100

> "This is the most important part. Here's what happens when 10,000 users click Buy Now simultaneously:"

> "Every request hits our Inventory Service and executes `SELECT ... FOR UPDATE`. This is a pessimistic database lock — it serialises all 10,000 threads at the database row level."

> "Thread 1 gets the lock. It checks: `available_quantity >= 1`. Yes. It decrements available, increments reserved, and commits. Thread 2 now gets the lock. Checks. Still enough. Thread 100 gets the lock. Checks. Commits. Thread 101 gets the lock. Checks: `available_quantity = 0`. Immediately returns 409 Sold Out."

> "There is physically no way for two threads to both see `available = 1` and both successfully decrement it, because only one thread holds the lock at any given moment."

> "Our PostgreSQL schema also enforces a CHECK constraint: `available + reserved + sold = total`. This is a second line of defence at the database itself."

> "Our Python simulation with 10,000 concurrent threads — which you can run right now — proves this invariant: 0 overselling events detected across every run."

---

## ⏱ 03:00 – 03:45 — Payment + Order Recovery

> "Now the critical failure scenario: payment succeeds, but our Order Service crashes."

> "Here's what we do differently. Payment Service writes the payment to PostgreSQL — status PENDING. Then it calls the gateway. Gateway says success. We update status to SUCCESS and — critically — publish a `PaymentConfirmedEvent` to Kafka before returning to the customer."

> "Kafka is a durable event log. Even if Order Service is completely dead, the event sits in Kafka for up to 7 days waiting to be consumed."

> "When Order Service restarts, it picks up from its last committed offset. It processes the event. But before creating the order, it checks the idempotency key — if the order already exists, it skips. No duplicate orders."

> "This is the Outbox + Kafka pattern. It gives us exactly-once business semantics with at-least-once infrastructure."

---

## ⏱ 03:45 – 04:30 — LLD + SOLID + Design Patterns

> "At the code level, our design is deliberate."

> "SOLID: Single Responsibility — InventoryService manages inventory. PaymentService manages payments. CheckoutService orchestrates via the Facade pattern — the controller calls one method and gets a CheckoutResult. Order state transitions are guarded by the State pattern — calling `order.transitionTo(DELIVERED)` after `DELIVERED` throws `IllegalStateException`."

> "Repository pattern makes every service storage-agnostic — in tests, we swap PostgreSQL for in-memory mocks with a single annotation."

> "Our ReservationExpiryScheduler uses a conditional SQL UPDATE — `WHERE status = RESERVED` — making the expiry operation idempotent. Even if two pods run the sweep simultaneously, only one will see `affected_rows = 1`. The other gets 0 and skips. Zero double-releases."

---

## ⏱ 04:30 – 05:00 — Scalability, Reliability & AI-Assisted Validation

> "For scalability: every backend instance is stateless. Redis handles session data and rate limiting. We can add instances behind the load balancer without coordination."

> "For reliability: our k6 load test hammers 10,000 virtual users at the reservation endpoint with 30-second window. Our Python simulation proves the mathematical invariant holds. Prometheus and Grafana give us real-time visibility into P95/P99 latency, error rate, and inventory state."

> "AI accelerated our documentation, boilerplate, and diagram generation. Every architecture decision — concurrency strategy, sync/async boundaries, Kafka vs REST, idempotency design — was made by our team and documented in 7 Architecture Decision Records."

> "The question SALESTORM answers is simple: can we guarantee that exactly 100 units, and not one more, are sold? Our answer, backed by simulation, code, and architecture, is yes."

---

*Total: 5 minutes*
