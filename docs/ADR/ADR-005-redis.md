# ADR-005 — Redis Caching Strategy

**Status:** Accepted  
**Date:** 2026-10-05  
**Deciders:** TeamPanda

---

## Context

During a flash sale, a single product page is hit by 10,000+ users in seconds. Without caching:
- Every product page view queries PostgreSQL
- 10,000 concurrent `SELECT * FROM inventory WHERE product_id = 101` hits the DB simultaneously
- This is unnecessary — most requests just want to *see* the current stock level, not modify it

We also need to consider rate limiting, session management, and idempotency key storage as potential Redis use cases.

---

## Decision

We use **Redis** as an in-memory cache and rate limiter, with the following strategy:

### 1. Inventory Count Cache
- Cache `inventory:product:{id}:available` with a **short TTL (1-2 seconds)**
- Frontend polls this to show real-time stock counter
- The actual inventory mutation always goes to PostgreSQL — Redis is read-only for display

### 2. Rate Limiting
- Use Redis sliding-window counters per IP/customer: `ratelimit:{ip}:{window}`
- Block customers attempting > 10 buy requests per minute

### 3. Idempotency Key Cache (optional)
- Short-lived TTL cache for idempotency keys avoids DB hits for repeated requests
- Primary source of truth remains PostgreSQL `UNIQUE` constraint

### 4. Session / Auth Tokens
- JWT invalidation list (blacklist) for signed-out tokens

---

## Cache-Aside Pattern

```
Read flow:
  1. Check Redis
  2. HIT → return cached value
  3. MISS → query PostgreSQL → write to Redis → return

Write flow (inventory reservation):
  1. Always write to PostgreSQL (source of truth)
  2. Invalidate / update Redis cache after successful DB commit
```

**We never trust Redis for inventory reservation.** The DB is always authoritative.

---

## What happens if Redis fails?

- The system degrades gracefully: product pages fall back to PostgreSQL reads (slower but correct)
- Rate limiting is temporarily disabled — acceptable for a short window
- Inventory reservation is unaffected (Redis is not in the write path)
- Circuit breaker wraps Redis calls to prevent cascading failure

---

## Alternatives Considered

- **No cache**: Simpler, but PostgreSQL gets hammered during the flash sale peak — high read latency, connection pool exhaustion
- **Memcached**: No persistence, no pub/sub, no rate limiting primitives — Redis is strictly more capable for our use case

## Consequences

- Redis must be considered a non-durable cache (TTL-based, evictable)
- Never use Redis as the source of truth for inventory numbers
- Production deployment must use Redis Sentinel or Redis Cluster for HA
