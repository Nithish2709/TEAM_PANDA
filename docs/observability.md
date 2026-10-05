# SALESTORM Observability Strategy

To operate a high-scale flash sale system reliably, we must be able to see exactly what is happening in real-time. This document defines the observability pillars for the SALESTORM architecture.

## 1. Metrics (Prometheus & Grafana)

We expose operational metrics to Prometheus, which are then visualized in Grafana. During a flash sale, monitoring standard CPU/RAM is not enough; we must monitor business metrics.

### Critical Business Metrics
- `successful_reservations_total`: Counter for HTTP 201 responses.
- `rejected_sold_out_total`: Counter for HTTP 409 responses.
- `inventory_oversell_detected`: Must remain strictly `0`. Checked via background invariant validation.
- `duplicate_requests_caught`: Counter tracking how many times the idempotency check saved the system.

### Performance Metrics
- `reservation_api_latency_seconds`: Histogram tracking API latency (focusing on P95 and P99 percentiles).
- `database_lock_wait_time`: Time spent waiting to acquire the pessimistic row lock.
- `message_queue_backlog`: Number of `PaymentSucceeded` events waiting for the Order Service.

## 2. Distributed Tracing (OpenTelemetry)

In a microservices or event-driven architecture, a single user click spans multiple boundaries. We inject a `trace_id` at the API Gateway and propagate it through HTTP headers and Kafka message headers.

**Trace Path:**
`Gateway` → `CheckoutFacade` → `InventoryService` (DB Lock) → `PaymentService` (External Call) → `Kafka` → `OrderService`.

This allows us to answer: *"Why did Order X take 35 seconds to confirm?"* by looking at the trace waterfall and seeing the 30-second delay was in the Payment Gateway block.

## 3. Structured Logging

We mandate structured JSON logging across all services. This allows logs to be efficiently parsed and queried in centralized logging systems (e.g., ELK stack or Datadog).

Every log entry MUST contain:
```json
{
  "timestamp": "2026-10-05T12:00:01.000Z",
  "level": "INFO",
  "service": "inventory-service",
  "trace_id": "8f3a9b...",
  "request_id": "req-12345",
  "customer_id": "5001",
  "idempotency_key": "IDEM-888",
  "message": "Successfully acquired pessimistic lock and reserved 1 unit of Product 101."
}
```

### Critical Log Events to Audit:
- Any `PaymentGatewayTimeoutException`.
- Any `DuplicateIdempotencyKeyDetected`.
- Any `OrderRecoveryTriggered` (when the Order Service picks up a delayed payment event).
