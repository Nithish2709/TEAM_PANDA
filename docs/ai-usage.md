# AI Usage — SALESTORM | SYSCRAFTERS 2026

## AI Tool Used

- **Antigravity IDE (Google DeepMind)** — AI coding assistant used throughout development

---

## What AI Was Used For

### Documentation & ADRs
AI generated initial drafts for:
- `requirements.md`, `architecture.md`, `concurrency.md`, `database.md`
- All 7 ADRs (ADR-001 through ADR-007)
- `failure-scenarios.md`, `solid.md`, `design-patterns.md`
- `communication-strategy.md`, `payment-reliability.md`
- `final-pitch.md`, `jury-questions.md`

Each was reviewed and corrected by the team for accuracy and to match our actual design.

### Code Generation
AI generated:
- Domain entities (`Inventory`, `InventoryReservation`, `Payment`, `Order`) — reviewed for correctness of JPA annotations and constraints
- Repository interfaces with custom `@Query` methods — reviewed for correctness of JPQL
- Service implementations (`InventoryService`, `PaymentService`, `OrderService`, `CheckoutService`) — reviewed for correct transaction boundaries and idempotency logic
- `ReservationExpiryScheduler` — reviewed for the conditional-UPDATE idempotency approach
- `ReservationController` REST endpoints — reviewed for correct HTTP status codes per spec
- `init.sql` PostgreSQL schema — reviewed for constraint correctness especially the `CHECK (available + reserved + sold = total)` invariant
- `docker-compose.yml` full stack — reviewed for service dependencies and health checks
- Mermaid diagrams (all 11 diagrams) — reviewed for accuracy vs. actual implementation
- k6 load test (`tests/load-test.js`) — reviewed for correct idempotency key simulation
- Python concurrency simulation (`simulation/simulate.py`) — reviewed for correct lock behaviour

### Frontend Dashboard
AI generated the React dashboard (`App.tsx`, `App.css`, `index.css`) — reviewed for correct API endpoint usage.

---

## Architecture Decisions Made by the Team

The following were **not delegated to AI** — they required domain reasoning:

| Decision | Reasoning |
|---|---|
| **Pessimistic over Optimistic locking** | We evaluated retry storm behaviour under flash-sale contention and chose pessimistic |
| **Synchronous reservation** | We determined the customer experience requires an immediate reservation result |
| **Kafka for payment → order** | We identified the exact failure scenario (payment success + order crash) and chose event-driven recovery |
| **Idempotency key per operation** | We decided to scope idempotency keys per operation type (RES-, ORD-, PAY- prefixes) |
| **Reservation 5-minute TTL** | Balances user experience (time to pay) vs. inventory lock duration |
| **Conditional UPDATE for expiry** | We chose the `WHERE status = RESERVED` pattern for idempotent multi-pod safety |

---

## What Was Manually Reviewed

- All concurrency logic in `InventoryService` — verified lock semantics
- All idempotency guards in service methods — verified no double-processing paths
- Order state machine transitions — verified no illegal paths exist
- Database constraint in `init.sql` — verified the CHECK constraint enforces the invariant
- Kafka event flow for order recovery — verified the failure scenario is correctly handled
- k6 test thresholds — verified P99 < 2s is realistic for the pessimistic lock approach

## What Was Changed After AI Generation

- `InventoryService.releaseReservation()`: AI initially did a direct status update; we changed it to the conditional `releaseIfStillReserved()` pattern to prevent double-release
- `CheckoutService`: AI initially called OrderService before PaymentService; we reordered to avoid creating ghost orders that never get paid
- `Order.canTransitionTo()`: AI generated a linear chain; we added the PAYMENT_FAILED → CANCELLED path for the failure scenario
- `init.sql`: AI did not initially include the `CHECK (available + reserved + sold = total)` constraint; we added it as a second line of defence
