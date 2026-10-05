# Failure Scenarios — SALESTORM

This document maps each failure scenario to its detection, response, retry, compensation, and final consistent state.

---

## 1. Inventory Database Failure

| Field | Detail |
|---|---|
| **Failure** | PostgreSQL becomes unreachable during a reservation attempt |
| **Detection** | JDBC connection exception / Spring Data `DataAccessException` |
| **Immediate Response** | `reserveInventory()` throws; controller returns HTTP 503 |
| **Retry?** | Yes — with exponential backoff (max 3 attempts, 1s/2s/5s) |
| **Compensation** | None needed — no data was written |
| **Recovery** | DB comes back; subsequent requests succeed normally |
| **Final State** | Inventory unchanged; customer sees "Service unavailable, try again" |

---

## 2. Payment Gateway Failure

| Field | Detail |
|---|---|
| **Failure** | Payment gateway returns 5xx or connection refused |
| **Detection** | HTTP client exception / `SimulatedFailureException` |
| **Immediate Response** | Payment status set to `FAILED`; reservation released |
| **Retry?** | Yes, with idempotency key — safe to retry without double-charge |
| **Compensation** | `releaseReservation()` — inventory returned to available pool |
| **Recovery** | Customer can retry checkout; new idempotency key or same key returns `FAILED` result |
| **Final State** | Inventory available again; payment record with `FAILED` status persisted |

---

## 3. Payment Timeout

| Field | Detail |
|---|---|
| **Failure** | Gateway accepts request but response takes > 30s (or never arrives) |
| **Detection** | HTTP client read timeout |
| **Immediate Response** | Payment status set to `TIMEOUT`; reservation released |
| **Retry?** | Safe with same idempotency key — gateway deduplicates |
| **Compensation** | Release reservation to avoid stuck inventory |
| **Recovery** | Reconciliation job checks gateway for transaction reference status |
| **Final State** | If gateway confirms success → order created. If genuine timeout → inventory released |

> ⚠️ **Critical rule**: Never charge twice. Always use idempotency key when retrying payment.

---

## 4. Duplicate Payment Request

| Field | Detail |
|---|---|
| **Failure** | Client sends the same payment request twice (double-click, network retry) |
| **Detection** | `PaymentRepository.findByIdempotencyKey()` finds existing record |
| **Immediate Response** | Return cached payment result — no gateway call |
| **Retry?** | N/A — idempotent response |
| **Compensation** | None needed |
| **Final State** | One payment record; same result returned to both requests |

---

## 5. Duplicate Buy Request (Double-Click)

| Field | Detail |
|---|---|
| **Failure** | User clicks "Buy Now" twice in rapid succession, both carry same `Idempotency-Key` |
| **Detection** | `InventoryReservationRepository.findByIdempotencyKey()` finds first reservation |
| **Immediate Response** | Return first reservation result without creating a second |
| **Retry?** | N/A |
| **Compensation** | None — one reservation, one unit consumed |
| **Final State** | Single reservation record; inventory decremented once |

---

## 6. Reservation Expiry

| Field | Detail |
|---|---|
| **Failure** | Customer reserved inventory but abandoned checkout before paying |
| **Detection** | `ReservationExpiryScheduler` finds `status=RESERVED AND expires_at < NOW()` |
| **Immediate Response** | Conditional UPDATE: `SET status=RELEASED WHERE status=RESERVED` |
| **Retry?** | Idempotent — second sweep finds `status=RELEASED`, skips |
| **Compensation** | `UPDATE inventory SET available++ , reserved--` |
| **Final State** | Inventory returned; reservation marked `RELEASED` |

---

## 7. Order Service Unavailable (30 seconds)

| Field | Detail |
|---|---|
| **Failure** | Order Service crashes after payment succeeds |
| **Detection** | `ORDER_SERVICE_CRASH` exception in `OrderService.createOrder()` |
| **Immediate Response** | `PaymentConfirmedEvent` already published to Kafka before crash |
| **Retry?** | Yes — Kafka retries delivery; Order Service resumes from offset on restart |
| **Compensation** | None — Kafka event is durable; will be consumed on recovery |
| **Final State** | Order created eventually; customer notified; no data lost |

---

## 8. Kafka / Message Broker Failure

| Field | Detail |
|---|---|
| **Failure** | Kafka broker goes down |
| **Detection** | Kafka producer exception when publishing event |
| **Immediate Response** | Payment Service retries with backoff; falls back to synchronous order creation |
| **Retry?** | Yes — Kafka producer retries; message stored in producer buffer |
| **Compensation** | If Kafka unavailable long-term, order must be created synchronously as fallback |
| **Final State** | Event delivered once Kafka recovers; idempotent consumer prevents duplication |

---

## 9. Consumer Crash Mid-Processing

| Field | Detail |
|---|---|
| **Failure** | Order Service consumer crashes after receiving event but before committing offset |
| **Detection** | Kafka redelivers event to next consumer instance in group |
| **Immediate Response** | Consumer receives event again on restart |
| **Retry?** | Kafka retries automatically |
| **Compensation** | Order Service checks idempotency key — if order exists, skips creation |
| **Final State** | Exactly-one order per payment; offset committed after success |

---

## 10. Notification Failure

| Field | Detail |
|---|---|
| **Failure** | Email/SMS provider returns 5xx |
| **Detection** | HTTP client exception in Notification Service |
| **Immediate Response** | Log failure; retry with backoff |
| **Retry?** | Yes — up to 5 retries |
| **Compensation** | None — notification is non-critical; order is already confirmed |
| **DLQ** | After retries exhausted, move to `notification.dlq` for manual investigation |
| **Final State** | Order unaffected; notification may be delayed or missed |

---

## 11. Database Connection Pool Exhaustion

| Field | Detail |
|---|---|
| **Failure** | All 50 HikariCP connections are in use; new requests cannot get a connection |
| **Detection** | `SQLTimeoutException` or HikariCP pool timeout log |
| **Immediate Response** | Return HTTP 503 with `Retry-After` header |
| **Retry?** | Client retries after backoff |
| **Compensation** | None — no write occurred |
| **Prevention** | Rate limiter (Redis) at API Gateway limits concurrent requests per second |
| **Final State** | Connections freed as transactions complete; pool drains naturally |

---

## 12. Traffic Spike (50× Normal Load)

| Field | Detail |
|---|---|
| **Failure** | 500,000 requests/sec suddenly arrive |
| **Detection** | Prometheus alerts on request rate and error rate |
| **Immediate Response** | WAF / CDN rate limiting absorbs initial shock; API Gateway rejects excess |
| **Retry?** | Clients receive 429 Too Many Requests with `Retry-After` |
| **Compensation** | Horizontal auto-scaling adds backend instances |
| **Final State** | System degrades gracefully; legitimate users eventually served |

---

## 13. Cache Failure (Redis Down)

| Field | Detail |
|---|---|
| **Failure** | Redis becomes unreachable |
| **Detection** | Redis client connection exception |
| **Immediate Response** | Fall back to direct PostgreSQL reads (cache-aside degrades gracefully) |
| **Retry?** | No retry needed — just bypass cache |
| **Compensation** | None — Redis is not in the write path for inventory |
| **Final State** | Higher PostgreSQL load (read replicas absorb it); correctness unaffected |
