# SALESTORM — SYSCRAFTERS 2026

> **Design-First, AI-Assisted High-Scale E-Commerce Flash Sale System**  
> TeamPanda | SYSCRAFTERS Hackathon

---

## 🎯 Problem Statement

Build a scalable high-traffic e-commerce architecture capable of handling **10,000 concurrent purchase attempts** for only **100 available units**, while preventing:
- Overselling
- Duplicate reservations
- Duplicate payments
- Incorrect order states

---

## ✅ Hard Guarantees

| Guarantee | Mechanism |
|---|---|
| `successful_sales ≤ 100` | Pessimistic DB lock (`SELECT ... FOR UPDATE`) |
| `available_quantity ≥ 0` | DB `CHECK` constraint + application guard |
| No duplicate reservations | Unique constraint on `idempotency_key` |
| No duplicate payments | Idempotency key lookup before gateway call |
| Payment success → Order always created | Kafka event survives Order Service crash |

---

## 🏗️ Architecture

```
Users (10,000)
     ↓
CDN / WAF
     ↓
Load Balancer
     ↓
API Gateway (Rate Limiting)
     ↓
┌─────────────────────────────────┐
│  Inventory & Reservation Service │ ← CRITICAL PATH (pessimistic lock)
│  Checkout Service (Facade)       │
│  Payment Service                 │
│  Order Service                   │
│  Shipment Service                │
│  Notification Service            │
└─────────────────────────────────┘
     ↓                    ↓                ↓
PostgreSQL            Redis            Kafka
(Source of Truth)    (Cache+RateLimit)  (Async Events)
     ↓
Prometheus + Grafana (Observability)
```

---

## 🔧 Technology Stack

| Layer | Technology |
|---|---|
| **Frontend** | React + TypeScript + Vite |
| **Backend** | Java 21 + Spring Boot 3.2 |
| **Database** | PostgreSQL 16 |
| **Cache** | Redis 7 |
| **Messaging** | Apache Kafka |
| **Observability** | Prometheus + Grafana + Actuator |
| **Testing** | JUnit 5 + Mockito + k6 |
| **Containers** | Docker + Docker Compose |
| **Diagrams** | Mermaid |

---

## 🚀 How to Run

### Prerequisites
- Docker Desktop
- Java 21 (for running backend locally)
- Node.js 20 (for running frontend locally)
- Python 3.10+ (for simulation)
- k6 (for load testing)

### Option A — Full Docker Stack (Recommended)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| H2 Console (local) | http://localhost:8080/h2-console |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin / salestorm) |

### Option B — Local Development (No Docker)

```bash
# Backend (uses H2 in-memory DB by default)
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

---

## 🧪 Running Tests

### JUnit Unit Tests
```bash
cd backend
./mvnw test
```

### Python Concurrency Simulation (10,000 threads)
```bash
python simulation/simulate.py
```

Expected output:
```
Successful Reservations : <= 100
Overselling             : 0
Negative Inventory      : 0
Invariant Maintained    : True
```

### k6 Load Test (requires running backend)
```bash
k6 run tests/load-test.js
```

---

## 📊 Key Results

| Metric | Value |
|---|---|
| Max successful reservations | ≤ 100 |
| Overselling events | 0 |
| Negative inventory | 0 |
| Duplicate payments | 0 |
| k6 P95 latency target | < 1000ms |
| k6 P99 latency target | < 2000ms |

See [`simulation/results.md`](simulation/results.md) for detailed simulation output.

---

## 📁 Repository Structure

```
SALESTORM/
├── backend/                      # Spring Boot (Java 21)
│   ├── src/main/java/com/salestorm/
│   │   ├── domain/               # Inventory, InventoryReservation, Payment, Order
│   │   ├── repository/           # Spring Data JPA repositories
│   │   ├── service/              # InventoryService, PaymentService, OrderService, CheckoutService
│   │   ├── controller/           # ReservationController, ApiController
│   │   ├── scheduler/            # ReservationExpiryScheduler
│   │   └── simulator/            # FailureSimulator (jury demo)
│   └── src/test/                 # JUnit tests
├── frontend/                     # React + TypeScript + Vite
├── docs/
│   ├── ADR/                      # 7 Architecture Decision Records
│   ├── diagrams/                 # 11 Mermaid diagrams
│   ├── architecture.md
│   ├── concurrency.md
│   ├── database.md
│   ├── failure-scenarios.md
│   ├── solid.md
│   ├── design-patterns.md
│   ├── communication-strategy.md
│   ├── payment-reliability.md
│   ├── jury-questions.md
│   ├── final-pitch.md
│   └── ai-usage.md
├── infrastructure/
│   ├── postgres/init.sql         # DB schema + seed data
│   └── monitoring/prometheus.yml
├── simulation/
│   ├── simulate.py               # Python 10,000-thread simulation
│   └── results.md
├── tests/
│   └── load-test.js              # k6 flash-sale load test
├── docker-compose.yml            # Full stack: Postgres+Redis+Kafka+Backend+Frontend+Prometheus+Grafana
└── .env.example
```

---

## 🔒 Security

- No credentials hardcoded — all via environment variables (`.env.example`)
- CORS configured for local development (`CorsConfig.java`)
- Rate limiting via Redis (architectural decision — ADR-005)
- Idempotency key validation on all mutating endpoints

---

## 📈 Observability

- **Prometheus**: Scrapes `/actuator/prometheus` every 5 seconds
- **Grafana**: Pre-configured dashboard at `:3001`
- **Key metrics**: Request rate, P50/P95/P99 latency, inventory counters, error rate
- **Logs**: Structured with correlation IDs at `INFO` level

---

## 📋 Architecture Diagrams

| # | Diagram | File |
|---|---|---|
| 01 | System Context | [`docs/diagrams/01-system-context.mmd`](docs/diagrams/01-system-context.mmd) |
| 02 | High-Level Design | [`docs/diagrams/02-hld.mmd`](docs/diagrams/02-hld.mmd) |
| 03 | Container | [`docs/diagrams/03-container.mmd`](docs/diagrams/03-container.mmd) |
| 04 | Component | [`docs/diagrams/04-component.mmd`](docs/diagrams/04-component.mmd) |
| 05 | Deployment | [`docs/diagrams/05-deployment.mmd`](docs/diagrams/05-deployment.mmd) |
| 06 | ER Diagram | [`docs/diagrams/06-er-diagram.mmd`](docs/diagrams/06-er-diagram.mmd) |
| 07 | Reservation Sequence | [`docs/diagrams/07-reservation-sequence.mmd`](docs/diagrams/07-reservation-sequence.mmd) |
| 08 | Payment Sequence | [`docs/diagrams/08-payment-sequence.mmd`](docs/diagrams/08-payment-sequence.mmd) |
| 09 | Purchase Sequence (Full) | [`docs/diagrams/09-purchase-sequence.mmd`](docs/diagrams/09-purchase-sequence.mmd) |
| 10 | Reservation State | [`docs/diagrams/10-reservation-state.mmd`](docs/diagrams/10-reservation-state.mmd) |
| 11 | Order State | [`docs/diagrams/11-order-state.mmd`](docs/diagrams/11-order-state.mmd) |

---

## 📚 Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-001](docs/ADR/ADR-001-sql-vs-nosql.md) | SQL (PostgreSQL) chosen over NoSQL |
| [ADR-002](docs/ADR/ADR-002-sync-vs-async.md) | Synchronous reservation, async order creation |
| [ADR-003](docs/ADR/ADR-003-concurrency-strategy.md) | Pessimistic locking chosen over optimistic |
| [ADR-004](docs/ADR/ADR-004-kafka.md) | Kafka for event-driven recovery |
| [ADR-005](docs/ADR/ADR-005-redis.md) | Redis for caching + rate limiting (not source of truth) |
| [ADR-006](docs/ADR/ADR-006-idempotency.md) | Idempotency keys on all mutating operations |
| [ADR-007](docs/ADR/ADR-007-order-recovery.md) | Kafka-based order recovery after payment success |

---

## 🎯 Hackathon Demo Instructions

1. Start all services: `docker compose up --build`
2. Open frontend: http://localhost:3000
3. Run the simulation: `python simulation/simulate.py`
4. Show the live dashboard updating inventory counters
5. Click "Simulate Payment Failure" — show reservation released
6. Click "Simulate Order Crash" — explain Kafka recovery path
7. Run k6 test: `k6 run tests/load-test.js` — show P95/P99 results
8. Open Grafana: http://localhost:3001 — show real-time metrics

> See [`docs/final-pitch.md`](docs/final-pitch.md) for the full 5-minute pitch script.