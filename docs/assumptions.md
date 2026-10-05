# SALESTORM Assumptions

This document outlines the core assumptions made during the design and implementation of the SALESTORM flash sale system.

## 1. Infrastructure Assumptions
- **Cloud/Deployment**: The system is intended to run in a containerized environment (e.g., Docker, Kubernetes). For the hackathon prototype, Docker Compose will simulate this environment.
- **Network**: The API Gateway/Load Balancer handles initial TLS termination, rate limiting, and basic web application firewall (WAF) duties.

## 2. Business Logic Assumptions
- **User Authentication**: Users are authenticated before participating in the flash sale. The system receives a valid `customer_id` via a JWT or similar trusted token in the API Gateway.
- **Product Scope**: The hackathon simulation focuses on a single highly contested product (e.g., "Gaming GPU" with 100 units).
- **Payment Gateway**: The system interacts with an external Payment Gateway. This gateway provides a unique `transaction_reference` on success and supports idempotency keys for retries. 

## 3. Prototype Scope Assumptions
- While the high-level architecture (HLD) dictates independent microservices for Product, Cart, Inventory, Payment, and Order, the **working prototype** will selectively combine some services (e.g., a monolithic backend for the prototype) to reduce deployment overhead while preserving the logical boundaries and database concurrency proofs required by the jury.
- **Message Broker (Kafka) & Cache (Redis)**: These are conceptually part of the architecture for handling asynchronous events (like `PaymentSuccess`) and rate limiting, but their necessity in the local prototype is subject to complexity trade-offs (as allowed by `readme2.md`).

## 4. Simulated Failure Assumptions
- Failures (e.g., Payment Timeout, Order Service Crash) are injected artificially via dedicated test hooks to demonstrate system resilience and recovery.
