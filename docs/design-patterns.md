# Design Patterns — SALESTORM

Each pattern below solves a real problem in this system.

---

## 1. Facade Pattern — `CheckoutService`

**Problem:** The purchase workflow spans 3 services (Inventory → Payment → Order). The controller should not need to know all the steps, ordering, and error handling for each.

**Solution:** `CheckoutService` is the Facade. The controller calls one method (`checkout()`) and gets back a `CheckoutResult`. Internally, the facade orchestrates the full pipeline.

**Classes:** `CheckoutService`, `ReservationController`

**Benefit:** Controller is 3 lines of code to trigger a purchase. The complex pipeline is hidden behind a clean interface.

**Trade-off:** The facade becomes the orchestrator — if the pipeline grows complex, the facade becomes large. Mitigated by keeping each service focused (SRP).

---

## 2. State Pattern — `Order`

**Problem:** An order moves through many states (CREATED → PAYMENT_PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED). Invalid transitions (e.g., DELIVERED → PAYMENT_PENDING) must be impossible.

**Solution:** `Order.canTransitionTo()` and `Order.transitionTo()` enforce the state machine. Any attempt to make an invalid transition throws `IllegalStateException`.

```java
public void transitionTo(Status next) {
    if (!canTransitionTo(next)) {
        throw new IllegalStateException(
            "Invalid order transition: " + this.status + " → " + next);
    }
    this.status = next;
}
```

**Classes:** `Order`, `OrderService`

**Benefit:** Invalid state transitions are caught at the domain level, not scattered across service methods.

**Trade-off:** `canTransitionTo()` switch expression must be updated when new states are added.

---

## 3. Repository Pattern — All Data Access

**Problem:** Business logic should not embed SQL queries. Storage implementation (PostgreSQL today, maybe something else tomorrow) should be swappable.

**Solution:** `InventoryRepository`, `PaymentRepository`, `OrderRepository`, `InventoryReservationRepository` abstract all data access. Services call `findByIdempotencyKey()`, not raw SQL.

**Classes:** All `*Repository` interfaces + Spring Data JPA

**Benefit:** Services are storage-agnostic. In tests, repositories are replaced with mocks in a single annotation (`@MockBean`).

**Trade-off:** Spring Data JPA auto-generated queries can hide performance issues — requires explicit `@Query` for complex cases (e.g., `findByIdForUpdate`).

---

## 4. Strategy Pattern — Payment Gateway (Architecture Level)

**Problem:** We want to support multiple payment providers (Mock, Stripe, Razorpay) without modifying `PaymentService`.

**Solution:** A `PaymentGateway` interface defines the contract. Each provider is a Strategy:

```java
public interface PaymentGateway {
    PaymentResult charge(String ref, BigDecimal amount, String idempotencyKey);
}

public class MockGateway implements PaymentGateway { ... }
public class StripeGateway implements PaymentGateway { ... }
```

`PaymentService` receives a `PaymentGateway` instance via constructor injection. Switching providers requires only a configuration change.

**Classes:** `PaymentService`, `FailureSimulator` (current mock implementation)

**Benefit:** OCP compliant — new providers don't require changes to `PaymentService`.

**Trade-off:** Adds an abstraction layer; for a prototype with one provider, this is simple.

---

## 5. Circuit Breaker Pattern — Payment Gateway Calls

**Problem:** If the payment gateway is failing, we should stop hitting it with requests. Continuous retries waste resources and worsen the failure.

**Solution (Architecture):** Wrap gateway calls with a Circuit Breaker (Resilience4j in production):

```
CLOSED → failures increase
OPEN → requests blocked immediately (fail-fast)
HALF_OPEN → one test request
  → success: back to CLOSED
  → failure: back to OPEN
```

In the prototype, `FailureSimulator` simulates this behaviour by throwing `SimulatedFailureException`.

**Classes:** `PaymentService`, `FailureSimulator`

**Benefit:** Prevents cascading failure; gateway gets recovery time; system degrades gracefully.

**Trade-off:** Adds latency during HALF_OPEN state; requires careful threshold tuning.

---

## 6. Observer Pattern — Event-Driven Recovery (Kafka)

**Problem:** When payment succeeds, Order Service, Shipment Service, and Notification Service all need to react. Tightly coupling `PaymentService` to all of them creates a dependency nightmare.

**Solution:** `PaymentService` publishes `PaymentConfirmedEvent` to Kafka. Order Service, Shipment Service, and Notification Service are observers (Kafka consumers) that react independently.

**Classes:** `PaymentService` (publisher), Order/Shipment/Notification consumers

**Benefit:** Payment Service doesn't know or care about Order Service. New consumers can be added without modifying `PaymentService`.

**Trade-off:** Eventual consistency — Order may not be created the instant payment succeeds.

---

## 7. Idempotent Consumer Pattern

**Problem:** Kafka delivers events at-least-once. If Order Service processes `PaymentConfirmedEvent` and crashes before committing the Kafka offset, it will receive the same event again on restart — creating a duplicate order.

**Solution:** Before creating an order, `OrderService` checks: `orderRepository.findByIdempotencyKey(eventId)`. If found, skip processing and commit offset.

**Classes:** `OrderService.createOrder()`, `OrderRepository`

**Benefit:** Exactly-once business semantics with at-least-once delivery infrastructure.

**Trade-off:** Requires DB lookup for every event — acceptable given the low volume of order creations vs. reservation attempts.
