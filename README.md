# SALESTORM — SYSCRAFTERS 2026

## Design-First, AI-Assisted High-Scale E-Commerce Flash Sale System

You are the **lead system architect, senior backend engineer, database architect, reliability engineer, and technical documentation engineer** for this project.

We are participating in the **SALESTORM | SYSCRAFTERS 2026 Design-First, AI-Assisted System Design Hackathon**.

The official problem is:

> Build a scalable high-traffic e-commerce architecture capable of handling 10,000 concurrent purchase attempts for only 100 available units, while preventing overselling, duplicate reservations, duplicate payments, and incorrect order states.

The system must be designed so that an engineering team could understand, challenge, implement, and operate it.

IMPORTANT:
This is a **design-first hackathon**. Do not optimize for a huge amount of code. Architecture, concurrency correctness, failure handling, trade-offs, UML, database design, APIs, reliability, and technical explanation are more important than implementation.

The official required pipeline is:

Customer
→ Product Discovery
→ Cart
→ Inventory Check
→ Inventory Reservation
→ Checkout
→ Payment
→ Order
→ Fulfilment
→ Shipment
→ Notification
→ Delivery Tracking

The critical scenario is:

* Product X
* Initial stock = 100
* Concurrent customers = 10,000
* Payment success = 95%
* Payment failure = 5%
* Duplicate requests = 2%
* Order Service unavailable for 30 seconds

The architecture must guarantee:

**Successful sales <= 100**

and inventory must never become negative.

---

# 1. FIRST STEP — UNDERSTAND BEFORE CODING

Before writing application code:

1. Analyze the entire project requirements.
2. Create a `docs/requirements.md`.
3. List:

   * Functional requirements
   * Non-functional requirements
   * Hard guarantees
   * Performance targets
   * Scalability assumptions
   * Availability assumptions
   * Consistency requirements
   * Security requirements
   * Failure scenarios
4. Clearly separate:

   * MUST guarantee
   * SHOULD support
   * TARGET/ASSUMPTION

Do not start implementation until the architecture is clearly documented.

---

# 2. RECOMMENDED ARCHITECTURE

Design the system around:

Users
↓
CDN / WAF
↓
Load Balancer
↓
API Gateway
↓
Product Service
Cart Service
Sale Service
↓
Inventory / Reservation Service
↓
Checkout Service
↓
Payment Service
↓
Order Service
↓
Shipment / Fulfilment
↓
Notification

Supporting infrastructure should include appropriate components such as:

* Redis/cache
* Relational database
* Message broker
* Dead-letter queue
* Distributed tracing
* Centralized logging
* Metrics/monitoring
* Rate limiter
* Circuit breaker

Do not blindly follow this architecture.

For every major component, explain:

1. Why it exists
2. What problem it solves
3. Why it is placed there
4. What happens if it fails
5. How it scales
6. Whether communication is synchronous or asynchronous

Create:

`docs/architecture.md`

---

# 3. TECHNOLOGY STACK

Use a practical stack suitable for a student system-design project.

Preferred:

Frontend:

* React
* TypeScript
* Vite

Backend:

* Java 21
* Spring Boot
* Spring Web
* Spring Data JPA

Database:

* PostgreSQL

Cache:

* Redis

Messaging:

* Kafka

API documentation:

* OpenAPI / Swagger

Containerization:

* Docker
* Docker Compose

Observability:

* Prometheus
* Grafana
* OpenTelemetry if practical

Testing:

* JUnit
* Testcontainers where practical
* k6 or equivalent load testing

Diagrams:

* Mermaid
* PlantUML where appropriate

If a simpler implementation is better for the prototype, keep the architecture production-oriented while keeping the code understandable.

---

# 4. REPOSITORY STRUCTURE

Create this structure:

SALESTORM/
│
├── backend/
│   ├── api-gateway/
│   ├── product-service/
│   ├── cart-service/
│   ├── sale-service/
│   ├── inventory-service/
│   ├── checkout-service/
│   ├── payment-service/
│   ├── order-service/
│   ├── shipment-service/
│   └── notification-service/
│
├── frontend/
│
├── infrastructure/
│   ├── docker/
│   ├── postgres/
│   ├── redis/
│   ├── kafka/
│   ├── monitoring/
│   └── observability/
│
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── database.md
│   ├── api.md
│   ├── concurrency.md
│   ├── payment-reliability.md
│   ├── order-lifecycle.md
│   ├── scalability.md
│   ├── reliability.md
│   ├── security.md
│   ├── observability.md
│   ├── tradeoffs.md
│   ├── adr/
│   ├── uml/
│   └── ai-usage.md
│
├── simulation/
│   ├── concurrency-test/
│   ├── duplicate-request-test/
│   ├── reservation-expiry-test/
│   ├── payment-failure-test/
│   └── order-service-failure-test/
│
├── tests/
│
├── docker-compose.yml
└── README.md

---

# 5. DATABASE DESIGN

Design a relational database around these entities:

* CUSTOMER
* PRODUCT
* CATEGORY
* INVENTORY
* INVENTORY_RESERVATION
* CART
* CART_ITEM
* ORDER
* ORDER_ITEM
* PAYMENT
* SALE / DEAL / COUPON
* SHIPMENT
* NOTIFICATION

Inventory must contain at least:

* inventory_id
* product_id
* available_quantity
* reserved_quantity
* sold_quantity
* version
* updated_at

Reservation must contain:

* reservation_id
* product_id
* customer_id
* quantity
* status
* expires_at
* idempotency_key
* created_at
* updated_at

Payment must contain:

* payment_id
* order_id
* idempotency_key
* transaction_reference
* amount
* status
* provider
* timestamps

Design:

* Primary keys
* Foreign keys
* Unique constraints
* Indexes
* Check constraints
* Transaction boundaries
* Audit information

Create:

`docs/database.md`

and:

`docs/uml/database-er.mmd`

---

# 6. CRITICAL REQUIREMENT — INVENTORY CONCURRENCY

This is the most important part of the system.

10,000 users may request 100 units simultaneously.

The system MUST NEVER allow:

available_quantity < 0

and:

successful_sales > 100

Design and compare at least TWO concurrency approaches:

### Approach A

Optimistic locking using a version column.

Example conceptual operation:

UPDATE inventory
SET available_quantity = available_quantity - requestedQuantity,
reserved_quantity = reserved_quantity + requestedQuantity,
version = version + 1
WHERE product_id = ?
AND available_quantity >= requestedQuantity
AND version = ?

### Approach B

Pessimistic database locking.

Example:

SELECT inventory
FROM inventory
WHERE product_id = ?
FOR UPDATE;

Then update inventory inside the transaction.

Explain:

* Advantages
* Disadvantages
* Contention
* Throughput
* Failure behaviour
* Scaling implications

Then choose ONE approach for the primary implementation.

Document the decision in:

`docs/concurrency.md`

and create an ADR:

`docs/adr/ADR-001-inventory-concurrency.md`

IMPORTANT:

The inventory consistency boundary must be extremely clear.

The final design must make it obvious exactly where the 100 units are protected.

---

# 7. INVENTORY RESERVATION STATE MACHINE

Implement and document:

AVAILABLE
→ RESERVED
→ PAYMENT_PENDING
→ CONFIRMED
→ SOLD

Failure paths:

RESERVED
→ PAYMENT_FAILED
→ RELEASED

RESERVED
→ TIMEOUT
→ RELEASED

Reservations must have an expiry time.

Create a background mechanism that identifies expired reservations and releases inventory safely.

Expired reservations must not accidentally release inventory twice.

Use idempotency.

Create:

`docs/uml/reservation-state.mmd`

---

# 8. IDEMPOTENCY

Every business operation that can be repeated must be protected.

Examples:

* Buy request
* Reservation request
* Payment request
* Order creation
* Event processing

Implement idempotency keys.

If the same request arrives twice:

FIRST REQUEST:
Process normally.

SECOND REQUEST:
Return the previous result instead of performing the business transaction again.

The system must distinguish:

* Same request repeated
* Different request
* Same payment attempted twice
* Same event delivered twice

Create:

`docs/idempotency.md`

---

# 9. PAYMENT DESIGN

Payment must support:

### Success

Payment succeeds
→ confirm payment
→ continue order creation

### Failure

Payment fails
→ mark payment failed
→ release reservation
→ update order/customer state

### Timeout

Payment gateway times out.

DO NOT blindly retry and risk charging twice.

Use:

* idempotency key
* transaction reference
* retry policy
* reconciliation

### Duplicate payment

Same idempotency key must not create a second transaction.

### Payment succeeds but Order Service fails

This is a critical jury scenario.

Design:

Payment success
→ PaymentConfirmed event
→ message broker
→ Order Service

If Order Service is unavailable:

* event remains available
* retry
* consumer resumes
* idempotent event processing
* reconciliation if necessary

Do NOT simply lose the successful payment.

Create:

`docs/payment-reliability.md`

---

# 10. ORDER STATE MACHINE

Implement:

CREATED
→ PAYMENT_PENDING
→ CONFIRMED
→ PROCESSING
→ SHIPPED
→ OUT_FOR_DELIVERY
→ DELIVERED

Also define failure/cancellation states where necessary.

Prevent invalid transitions.

For example:

DELIVERED → PAYMENT_PENDING

must never happen.

Use the State design pattern where appropriate.

Create:

`docs/uml/order-state.mmd`

---

# 11. MESSAGE-DRIVEN ARCHITECTURE

Use asynchronous events where they improve reliability and scalability.

Examples:

ReservationCreated
ReservationExpired
PaymentSucceeded
PaymentFailed
OrderConfirmed
OrderProcessing
OrderShipped
OrderDelivered
NotificationRequested

For every event define:

* Producer
* Consumer
* Event schema
* Ownership
* Retry behaviour
* Idempotency behaviour
* Dead-letter behaviour

Create:

`docs/events.md`

---

# 12. SYNCHRONOUS VS ASYNCHRONOUS

Explicitly decide which operations are synchronous.

For example:

Synchronous:

Buy request
→ inventory reservation

because the customer needs an immediate reservation result.

Potentially asynchronous:

PaymentSucceeded
→ Order creation

OrderConfirmed
→ Notification

OrderConfirmed
→ Shipment

Explain every decision.

Create a table in:

`docs/communication-strategy.md`

---

# 13. LOW-LEVEL DESIGN

Create class diagrams for at least:

1. Inventory
2. Payment
3. Order

Show:

* Interfaces
* Implementations
* Dependencies
* Responsibilities
* Relationships

Use interfaces such as:

InventoryRepository
ReservationRepository
PaymentGateway
PaymentProcessor
OrderRepository
EventPublisher

Create:

`docs/uml/inventory-class.mmd`
`docs/uml/payment-class.mmd`
`docs/uml/order-class.mmd`

---

# 14. SOLID PRINCIPLES

Explicitly demonstrate:

### SRP

Separate:

* Payment processing
* Order management
* Notification

### OCP

Allow new:

* Payment providers
* Pricing strategies
* Delivery strategies

without modifying core business logic.

### LSP

Payment providers should be replaceable through their abstraction.

### ISP

Avoid huge interfaces.

### DIP

High-level services depend on abstractions rather than concrete providers.

Create:

`docs/solid.md`

with actual classes from the implementation.

Do not provide generic textbook explanations only.

Map each principle to the actual code.

---

# 15. DESIGN PATTERNS

Use patterns only when they solve real problems.

Consider:

* Strategy
* Factory
* State
* Observer
* Adapter
* Facade
* Repository
* Circuit Breaker

For every pattern document:

1. Where it is used
2. Problem it solves
3. Classes involved
4. Benefit
5. Trade-off

Create:

`docs/design-patterns.md`

---

# 16. CHECKOUT FACADE

Create a CheckoutFacade that orchestrates the purchase workflow.

Conceptually:

Customer
→ CheckoutFacade
→ Validate cart
→ Reserve inventory
→ Create order
→ Initiate payment
→ Publish events

Do not put every business rule inside the facade.

The facade should orchestrate, not own every responsibility.

---

# 17. API DESIGN

Create REST APIs.

At minimum:

POST /api/v1/reservations

POST /api/v1/reservations/{id}/confirm

POST /api/v1/reservations/{id}/release

POST /api/v1/checkout

POST /api/v1/payments

GET /api/v1/payments/{id}

POST /api/v1/orders

GET /api/v1/orders/{id}

GET /api/v1/products/{id}

POST /api/v1/cart/items

DELETE /api/v1/cart/items/{id}

For each API define:

* Method
* Endpoint
* Authentication
* Request
* Response
* HTTP status
* Validation
* Idempotency
* Error response

Generate OpenAPI documentation.

Create:

`docs/api.md`

---

# 18. FAILURE SCENARIOS

The system must explicitly handle:

1. Inventory database failure
2. Payment gateway failure
3. Payment timeout
4. Duplicate payment
5. Duplicate Buy request
6. Reservation expiry
7. Order Service unavailable
8. Kafka/message failure
9. Consumer crash
10. Notification failure
11. Database connection pool exhaustion
12. Traffic spike
13. Cache failure

For each scenario explain:

Failure
→ Detection
→ Immediate response
→ Retry?
→ Compensation?
→ Recovery
→ Final consistent state

Create:

`docs/failure-scenarios.md`

---

# 19. CIRCUIT BREAKER

External payment providers may fail.

Use a circuit breaker concept:

CLOSED
→ failures increase

OPEN
→ requests blocked

HALF_OPEN
→ test request

→ CLOSED if recovered

or

→ OPEN if failure continues

Do not blindly retry a failing external dependency.

---

# 20. RETRIES

Every retry must answer:

* What is being retried?
* How many times?
* Backoff?
* Is the operation idempotent?
* Could retry create duplicate business effects?
* What happens after retry exhaustion?

Use exponential backoff where appropriate.

Do not retry non-idempotent operations blindly.

---

# 21. DEAD LETTER QUEUE

Failed asynchronous messages should eventually move to a DLQ.

Document:

Main Queue
→ Consumer
→ Retry
→ Retry
→ Retry
→ DLQ

Provide a recovery/replay mechanism.

---

# 22. SCALABILITY

The system should reason about:

Normal traffic:
~10,000 requests/sec

Flash-sale traffic:
up to ~500,000 requests/sec

Explain:

* Horizontal scaling
* Load balancing
* Stateless services
* Redis caching
* Database scaling
* Read replicas
* Queueing
* Backpressure
* Rate limiting
* Connection pool limits

Identify the likely bottlenecks.

Especially analyze:

Inventory Service
Database
Payment Gateway
Order Service
Message Broker

Create:

`docs/scalability.md`

---

# 23. 10,000 → 100 SIMULATION

Create a working simulation.

Scenario:

100 inventory units
10,000 concurrent purchase attempts

Expected:

At most 100 successful reservations.

No negative inventory.

No duplicate successful reservation for the same idempotency key.

Create tests that prove this.

Example output:

Total requests: 10000
Successful reservations: <= 100
Failed reservations: ...
Duplicate requests: ...
Inventory remaining: ...
Overselling detected: 0
Negative inventory detected: 0

This simulation is extremely important because it gives us evidence for the jury.

---

# 24. PRACTICAL HACKATHON TEST CASE

Implement a configurable simulation:

STOCK = 100

USERS = 10000

PAYMENT_SUCCESS_RATE = 0.95

PAYMENT_FAILURE_RATE = 0.05

DUPLICATE_REQUEST_RATE = 0.02

ORDER_SERVICE_DOWNTIME = 30 seconds

The simulation should demonstrate:

### Test 1

10,000 users compete for 100 units.

### Test 2

Payment failure releases reservations.

### Test 3

Duplicate Buy request is idempotent.

### Test 4

Payment succeeds while Order Service is unavailable.

### Test 5

Reservation expires.

### Test 6

Inventory reaches zero.

### Test 7

Traffic increases 50×.

Generate a report:

`simulation/results.md`

---

# 25. OBSERVABILITY

Define metrics:

* Request rate
* P50 latency
* P95 latency
* P99 latency
* Error rate
* Reservation success rate
* Reservation failure rate
* Payment success rate
* Payment failure rate
* Order conversion
* Queue backlog
* Inventory inconsistency
* Duplicate request count

Logs should contain:

* correlation_id
* request_id
* user_id
* reservation_id
* payment_id
* order_id
* event_type
* timestamp
* status

Use distributed tracing for:

Checkout
→ Inventory
→ Payment
→ Order

Create:

`docs/observability.md`

---

# 26. SECURITY

Implement/document:

* Authentication
* Authorization
* HTTPS
* Input validation
* Rate limiting
* API abuse protection
* Secure payment handling
* Secrets through environment variables
* Audit logging

Never hardcode:

* Database passwords
* API keys
* Payment credentials
* JWT secrets

Create:

`.env.example`

with placeholders only.

---

# 27. FRONTEND

Create a simple but professional demo dashboard.

Pages:

### Home

Product X flash sale.

Display:

* Product
* Price
* Available stock
* Buy Now button
* Countdown

### Checkout

Display:

* Reservation status
* Payment status
* Order status

### Order Tracking

Display:

CREATED
→ PAYMENT_PENDING
→ CONFIRMED
→ PROCESSING
→ SHIPPED
→ OUT_FOR_DELIVERY
→ DELIVERED

### Admin/Simulation Dashboard

Show:

* Total requests
* Successful reservations
* Failed reservations
* Current inventory
* Payment success/failure
* Duplicate requests
* Queue backlog
* Error rate

This dashboard is primarily for demonstration.

---

# 28. UML / DIAGRAMS

Generate Mermaid diagrams for:

1. System Context
2. HLD
3. Container
4. Component
5. Deployment
6. ER Diagram
7. Inventory Class Diagram
8. Payment Class Diagram
9. Order Class Diagram
10. Purchase Sequence
11. Payment Sequence
12. Order Recovery Sequence
13. Reservation State Diagram
14. Order State Diagram

Store them under:

`docs/uml/`

Every diagram must correspond to the actual architecture.

Do not generate decorative diagrams that do not match the implementation.

---

# 29. ARCHITECTURE DECISION RECORDS

Create ADRs for at least:

ADR-001 Inventory concurrency strategy

ADR-002 SQL vs NoSQL

ADR-003 Synchronous vs asynchronous communication

ADR-004 Kafka/message broker

ADR-005 Redis caching

ADR-006 Payment idempotency

ADR-007 Order recovery after payment success

Each ADR must contain:

* Context
* Decision
* Alternatives considered
* Advantages
* Disadvantages
* Consequences

---

# 30. TRADE-OFF ANALYSIS

Explicitly compare:

### SQL vs NoSQL

### Optimistic vs pessimistic locking

### Synchronous vs asynchronous

### Strong consistency vs availability

### Redis vs database-only

### Kafka vs direct service calls

### Monolith vs microservices

The final architecture must explain WHY the selected solution is appropriate for this specific SALESTORM scenario.

Do not simply say "microservices are scalable."

Explain the actual trade-off.

---

# 31. ARCHITECTURE MUST BE SIMPLE ENOUGH TO DEFEND

IMPORTANT:

Do NOT create unnecessary microservices merely to make the architecture look impressive.

Every service must have a clear responsibility.

The final architecture must be explainable by students during a 5-minute pitch.

Prefer:

Correctness

>

Explainability

>

Reliability

>

Scalability

>

Complexity

---

# 32. FINAL DEMONSTRATION FLOW

Prepare a demo script.

The main demonstration must answer:

> "Your architecture has 100 units remaining and 10,000 customers are simultaneously clicking Buy Now. Walk us through exactly what happens from request arrival until the final valid orders are confirmed."

Trace:

User
→ CDN/WAF
→ Load Balancer
→ API Gateway
→ Sale Service
→ Inventory Service
→ Reservation
→ Checkout
→ Payment
→ Event Broker
→ Order Service
→ Shipment
→ Notification

Explain exactly where concurrency is controlled.

---

# 33. JURY QUESTIONS

Create:

`docs/jury-questions.md`

Answer at least:

1. Why this service boundary?
2. Where exactly is inventory consistency guaranteed?
3. What happens if two requests reach Inventory simultaneously?
4. Why did you choose optimistic/pessimistic locking?
5. Why is this operation synchronous?
6. Why is that operation asynchronous?
7. What happens if payment succeeds but Order Service fails?
8. What happens if payment times out?
9. How do you prevent duplicate payment?
10. What is the biggest bottleneck?
11. How does the system scale 50×?
12. What happens when Redis fails?
13. What happens when Kafka fails?
14. What happens when PostgreSQL fails?
15. Why PostgreSQL?
16. Why Redis?
17. Why Kafka?
18. Why not build everything as one monolith?
19. What consistency guarantees do you provide?
20. What trade-off did you consciously accept?

Answers must be based on our actual architecture.

---

# 34. 5-MINUTE PITCH

Generate:

`docs/final-pitch.md`

Follow exactly this structure:

30 sec — Problem

30 sec — Requirements

60 sec — HLD

60 sec — Critical 10,000 vs 100 inventory scenario

45 sec — Payment + Order recovery

45 sec — LLD + SOLID + Design Patterns

30 sec — Scalability + Reliability + AI-assisted validation

The pitch must focus on engineering decisions, not generic marketing language.

---

# 35. AI USAGE DOCUMENTATION

Create:

`docs/ai-usage.md`

Mention:

* AI tool used
* What AI was used for
* Architecture decisions made by students
* Code generated with AI
* Tests used to validate generated code
* What was manually reviewed
* What was changed after validation

Remember:

AI must accelerate implementation.

AI must NOT replace:

* Requirement analysis
* Architecture selection
* Concurrency reasoning
* Inventory consistency
* Payment/order state modelling
* Trade-off analysis
* Security decisions
* Reliability decisions
* Final explanation

---

# 36. README

Create a professional `README.md` containing:

1. Project overview
2. Problem statement
3. Architecture
4. Technology stack
5. Repository structure
6. How to run
7. Docker setup
8. API documentation
9. Database setup
10. Kafka setup
11. Redis setup
12. Simulation
13. Test results
14. Architecture diagrams
15. Failure scenarios
16. Scalability strategy
17. Security
18. Observability
19. AI usage
20. Hackathon demo instructions

---

# 37. DEVELOPMENT PROCESS

Follow this order.

PHASE 1:
Requirements

PHASE 2:
Architecture

PHASE 3:
Database

PHASE 4:
Inventory concurrency

PHASE 5:
Reservation lifecycle

PHASE 6:
Checkout

PHASE 7:
Payment idempotency

PHASE 8:
Order lifecycle

PHASE 9:
Event-driven recovery

PHASE 10:
Failure handling

PHASE 11:
Simulation

PHASE 12:
Observability

PHASE 13:
Frontend demo

PHASE 14:
UML + ADR

PHASE 15:
Testing

PHASE 16:
Final pitch

DO NOT jump directly to frontend development.

---

# 38. IMPORTANT CODING RULES

Write clean, understandable code.

Use:

* Meaningful names
* Interfaces
* DTOs
* Services
* Repositories
* Exception handling
* Validation
* Unit tests
* Integration tests

Avoid:

* Giant classes
* Giant controller methods
* Business logic inside controllers
* Hardcoded credentials
* Hardcoded inventory decisions
* Silent failures
* Duplicate business logic
* Overengineering

---

# 39. TESTING REQUIREMENTS

Write tests for:

### Inventory

* Reserve available stock
* Reject insufficient stock
* Concurrent reservation
* Never negative inventory
* Never exceed stock

### Idempotency

* Duplicate reservation
* Duplicate payment
* Duplicate event

### Reservation

* Expiry
* Release
* Confirm
* Double release protection

### Payment

* Success
* Failure
* Timeout
* Duplicate request

### Order

* Valid state transitions
* Invalid transitions
* Payment success + Order Service unavailable
* Retry/recovery

---

# 40. FINAL VALIDATION CHECKLIST

Before considering the project complete, verify:

[ ] 10,000 concurrent requests handled conceptually

[ ] 100 units never oversold

[ ] Inventory never negative

[ ] Duplicate Buy request handled

[ ] Duplicate payment handled

[ ] Reservation expiry works

[ ] Payment failure releases reservation

[ ] Payment timeout handled safely

[ ] Payment success + Order Service failure recovered

[ ] Order state transitions valid

[ ] Database constraints defined

[ ] API specification exists

[ ] Event specification exists

[ ] HLD exists

[ ] Container diagram exists

[ ] Component diagram exists

[ ] Deployment diagram exists

[ ] ER diagram exists

[ ] Class diagrams exist

[ ] Sequence diagrams exist

[ ] State diagrams exist

[ ] SOLID mapping exists

[ ] Design pattern mapping exists

[ ] ADRs exist

[ ] Scalability analysis exists

[ ] Security analysis exists

[ ] Observability exists

[ ] Failure scenarios documented

[ ] Concurrency simulation works

[ ] Tests pass

[ ] README complete

[ ] AI usage documented

[ ] 5-minute pitch prepared

---

# FINAL INSTRUCTION

Do not merely generate a large amount of code.

Build this as a **hackathon-ready system design project**.

At every major stage, verify that:

1. The architecture solves the original SALESTORM problem.
2. Inventory cannot oversell.
3. Duplicate operations are idempotent.
4. Failures have explicit recovery paths.
5. The design is horizontally scalable.
6. The database and event model support the lifecycle.
7. The UML matches the actual architecture.
8. The implementation matches the documented design.
9. Tests provide evidence for critical assumptions.
10. Every design decision can be defended to a jury.

The most important proof is:

**10,000 simultaneous Buy requests + 100 units → at most 100 successful sales, with correct reservation, payment, and order recovery.**

Do not finish by saying "the project is complete."

Instead, provide:

* What was implemented
* What was tested
* Test results
* Architecture summary
* Known limitations
* Remaining risks
* How to run the demo
* How to demonstrate the 10,000-vs-100 scenario
* Likely jury questions and answers