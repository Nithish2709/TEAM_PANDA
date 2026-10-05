# SOLID Principles — SALESTORM Implementation

This document maps each SOLID principle to **actual classes** in the codebase.

---

## S — Single Responsibility Principle

> A class should have only one reason to change.

### Applied In:

| Class | Single Responsibility |
|---|---|
| `InventoryService` | Manages inventory mutations and reservation lifecycle only |
| `PaymentService` | Processes payments and tracks payment status only |
| `OrderService` | Manages order creation and state transitions only |
| `CheckoutService` | Orchestrates the purchase pipeline — delegates to specialists |
| `ReservationExpiryScheduler` | Sweeps expired reservations — nothing else |
| `FailureSimulator` | Injects controlled failures for demo purposes only |
| `ReservationController` | HTTP request/response mapping only — no business logic |

### Anti-pattern avoided:

We do NOT put inventory, payment, and order logic inside `ReservationController`. Each service owns exactly one domain concern.

---

## O — Open/Closed Principle

> Open for extension, closed for modification.

### Applied In:

**Payment Providers**  
The `PaymentService` calls `failureSimulator.checkAndSimulate()` through the `FailureSimulator` abstraction. To add a new payment provider (Stripe, Razorpay, PayU), we extend the payment gateway integration without modifying `PaymentService`.

In the full architecture, a `PaymentGateway` interface would be defined:

```java
public interface PaymentGateway {
    PaymentResult charge(String transactionRef, BigDecimal amount, String idempotencyKey);
}
```

New providers implement this interface. `PaymentService` depends on `PaymentGateway`, not on `StripeGateway` or `RazorpayGateway`. Adding a new provider does not require changes to `PaymentService`.

**Order Status Transitions**  
`Order.canTransitionTo()` uses a `switch` expression. Adding new states (e.g., `RETURN_REQUESTED`) extends the switch without modifying existing case logic.

---

## L — Liskov Substitution Principle

> Subtypes must be substitutable for their base types.

### Applied In:

**Repository Interfaces**  
`InventoryRepository`, `PaymentRepository`, `OrderRepository`, `InventoryReservationRepository` are all Spring Data `JpaRepository` implementations. They can be swapped for test doubles (`@MockBean` in JUnit tests) without any change to the service layer.

```java
// In tests:
@MockBean InventoryRepository inventoryRepository;
// InventoryService sees the same interface — no modification needed
```

**Payment Gateway**  
`MockGateway` and `StripeGateway` (future) both implement `PaymentGateway`. Either can be injected into `PaymentService` without changing any caller behaviour.

---

## I — Interface Segregation Principle

> Clients should not be forced to depend on interfaces they do not use.

### Applied In:

Rather than one giant `IInventoryManager` interface with 20 methods, we have focused repositories:

| Repository | Exposed Methods |
|---|---|
| `InventoryRepository` | `findByIdForUpdate()` — only the critical locked read |
| `InventoryReservationRepository` | `findByIdempotencyKey()`, `findExpiredReservations()`, `releaseIfStillReserved()` |
| `PaymentRepository` | `findByIdempotencyKey()` |
| `OrderRepository` | `findByIdempotencyKey()` |

Each service only depends on the methods it actually needs. `ReservationExpiryScheduler` only depends on `InventoryService.releaseExpiredReservations()` — it does not have access to payment or order operations.

---

## D — Dependency Inversion Principle

> High-level modules should not depend on low-level modules. Both should depend on abstractions.

### Applied In:

**`CheckoutService` (high-level) depends on service abstractions, not concrete implementations:**

```java
public class CheckoutService {
    private final InventoryService inventoryService;   // interface-like abstraction
    private final PaymentService paymentService;
    private final OrderService orderService;

    // Constructor injection — Spring provides concrete instances
}
```

`CheckoutService` does not know that `InventoryService` uses JPA or PostgreSQL. It only calls `reserveInventory()`. The concrete implementation (JPA, pessimistic lock, PostgreSQL) is injected by Spring's IoC container.

**`ReservationController` depends on service abstractions:**

The controller never directly accesses `InventoryRepository`. It only calls `inventoryService.reserveInventory()`. Swapping the storage implementation (e.g., from PostgreSQL to a different DB) requires zero changes to the controller.

**Spring DI Container implements DI:**

All dependencies are injected via constructor injection — the recommended approach in Spring Boot 3. No `new ConcreteService()` calls appear in business logic classes.
