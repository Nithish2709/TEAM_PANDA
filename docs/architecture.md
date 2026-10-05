# SALESTORM Architecture

This document describes the High-Level Design (HLD) of the SALESTORM flash sale system. It details the logical architecture required for a production deployment and clarifies how this architecture maps to our small working prototype.

## 1. High-Level Architecture (Production)

The production architecture is designed to handle immense scale (e.g., 500,000 requests/sec) while strictly preserving correctness during a highly contentious flash sale.

```text
Customer
   ↓
CDN / WAF (DDoS protection, rate limiting, static asset caching)
   ↓
Load Balancer
   ↓
API Gateway (Authentication, basic routing, edge rate limiting)
   ↓
---------------------------------
| Logical Microservices         |
|                               |
| - Product Service             |
| - Cart Service                |
| - Sale Service                |
| - Inventory Service           |
| - Checkout Service            |
| - Payment Service             |
| - Order Service               |
| - Shipment Service            |
| - Notification Service        |
---------------------------------
       ↓
---------------------------------
| Data & Message Layer          |
|                               |
| - Redis (Caching, Rate limit) |
| - PostgreSQL (Relational DB)  |
| - Kafka (Message Broker)      |
---------------------------------
       ↓
---------------------------------
| Observability                 |
|                               |
| - Prometheus + Grafana        |
| - OpenTelemetry               |
| - Structured Logging          |
---------------------------------
```

## 2. Core Service Responsibilities

- **Inventory Service**: The most critical component. It manages stock, handles reservations (AVAILABLE → RESERVED), and guarantees that inventory never goes below zero. It interacts directly with the PostgreSQL DB where concurrency locks are enforced.
- **Checkout Service (Facade)**: Orchestrates the purchase flow. Validates the cart, requests an inventory reservation, initiates payment, and kicks off order creation.
- **Payment Service**: Interfaces with external payment gateways. It handles success, failure, timeouts, and idempotent retries.
- **Order Service**: Creates the final order. If the Order Service is down when a payment succeeds, the system uses an event-driven approach (Kafka) to recover and eventually create the order.

## 3. Production vs. Prototype Distinction

To ensure the prototype is explainable and manageable during the hackathon while retaining structural integrity, we apply the following constraints:

### Prototype Architecture

```text
Customer (Browser Dashboard)
   ↓
Prototype Backend (Spring Boot Monolith)
 [ Logical Separation internally ]
 ├── Inventory Module
 ├── Checkout Module
 ├── Payment Module
 └── Order Module
   ↓
PostgreSQL (Strict Concurrency Invariants Enforced Here)
```

**Why this approach?**
1. **Explainability**: A monolith with strict internal boundaries (modular monolith) is easier to debug and demonstrate than 8 separate containers.
2. **Concurrency Proof**: The core challenge of 10,000 requests vs 100 inventory units is solved at the **Database level** (locks, versioning, constraints). A modular monolith connected to PostgreSQL perfectly demonstrates this proof.
3. **Recovery Simulation**: We can simulate Order Service failure by artificially injecting errors in the `OrderModule` and proving our background recovery/retry logic still operates correctly. 

*Note: For the prototype, Kafka and Redis are considered optional. If omitted to keep the footprint small, event-driven recovery will be simulated via an internal async event bus and a persistent outbox pattern in PostgreSQL.*

## 4. Key Architectural Trade-offs

- **Consistency over Availability (CP over AP)**: For inventory deduction and order creation, we strictly prefer consistency. We would rather reject a valid user than accidentally oversell.
- **Synchronous vs Asynchronous**:
  - **Synchronous**: Inventory reservation MUST be synchronous to give the user immediate feedback and lock the item.
  - **Asynchronous**: Post-payment order creation and notification are asynchronous. If payment succeeds, we guarantee order creation eventually, even if the order service temporarily fails.
