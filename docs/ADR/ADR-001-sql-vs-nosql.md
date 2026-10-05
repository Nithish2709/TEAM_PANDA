# ADR 001: SQL vs NoSQL for Core Flash Sale Data

## Context
We need a data store to handle inventory counts and reservations during a massive flash sale spike (10,000 requests for 100 items).

## Decision
We chose a **Relational Database (SQL - PostgreSQL)** over NoSQL for the core inventory and reservation engine.

## Alternatives Considered
- NoSQL (MongoDB, Cassandra): High write throughput, horizontal scalability.
- Redis (In-memory): Extreme throughput, single-threaded atomicity.

## Why We Selected PostgreSQL
The defining challenge of this hackathon is absolute consistency (overselling is strictly forbidden). Relational databases provide strong ACID guarantees and Row-Level Locking (`SELECT ... FOR UPDATE`). We can use database-level constraints (`CHECK available_quantity >= 0`) as an ultimate safety net that cannot be bypassed by application bugs.

## Trade-offs
- **Gained**: Mathematical certainty that we will never oversell. Simple, centralized reasoning about concurrency.
- **Sacrificed**: Raw write throughput. Pessimistic locking creates a bottleneck on the specific product row being purchased. 

## Consequences
We must heavily optimize the lock duration. Network calls to payment gateways must *never* happen inside the database transaction holding the inventory lock. We will rely on connection pooling tuning to handle blocked threads waiting for the lock.
