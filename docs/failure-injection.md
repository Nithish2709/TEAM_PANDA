# SALESTORM Failure Injection Strategy

To prove our architecture is robust, the system includes a dedicated Failure Injection mechanism. This allows the hackathon jury to intentionally trigger catastrophic failures during the live demo and observe how the system recovers.

## 1. Simulated Scenarios

The frontend dashboard will feature explicit buttons triggering the following API configurations:

### Scenario A: Payment Failure
- **Trigger**: Sets a flag in the `FailureSimulator`.
- **Effect**: The `PaymentService` will mock a `402 Payment Required` from the gateway.
- **Expected Recovery**: The system catches the error, updates the payment state to `FAILED`, and asynchronously transitions the linked reservation from `PAYMENT_PENDING` to `RELEASED`, returning the inventory to the available pool.

### Scenario B: Payment Gateway Timeout
- **Trigger**: Sets a flag in the `FailureSimulator`.
- **Effect**: The `PaymentService` thread sleeps for 35 seconds, triggering a `SocketTimeoutException`.
- **Expected Recovery**: The system catches the timeout. Crucially, it does *not* blindly retry. It logs the timeout, marks the status `TIMEOUT`, and triggers a reconciliation job to check the gateway later, releasing the inventory in the meantime.

### Scenario C: Order Service Crash
- **Trigger**: Sets a flag causing the `OrderService.createOrder()` method to throw a simulated `OutOfMemoryError` or `RuntimeException`.
- **Effect**: Payment succeeds, but the application crashes right before generating the final order.
- **Expected Recovery**: The `PaymentSucceeded` event was already published to the persistent Outbox / Kafka. The background consumer will retry the event. Once the "crash" flag is disabled (simulating service restart), the order will be successfully generated from the event, ensuring no lost orders.

### Scenario D: Database Connection Drop
- **Trigger**: Simulates a transient `SQLException` during checkout.
- **Expected Recovery**: The transaction rolls back cleanly. If the user hits retry, the `idempotency_key` ensures they pick up right where they left off (if the reservation was already secured).

## 2. API Design for Injection

To control this from the frontend, we will expose a special admin endpoint:

```http
POST /api/admin/simulate-failure
Content-Type: application/json

{
  "failure_type": "ORDER_SERVICE_CRASH",
  "active": true
}
```

This endpoint alters the internal state of the `FailureSimulator` singleton bean, allowing us to control the exact flow of the demonstration.
