# SALESTORM Payment Reliability & Recovery

Payment processing involves external systems that can be slow, fail, or time out. Our architecture ensures that payment states are handled reliably without data loss or duplicate charges.

## 1. Payment Scenarios

The system handles the following scenarios robustly:
- **Success**: Payment succeeds. The system confirms the reservation and initiates order creation.
- **Failure**: Payment fails (e.g., insufficient funds). The system marks the payment as failed and triggers the release of the reservation.
- **Timeout**: The external gateway takes too long. The system does NOT blindly retry. It queries the payment gateway using the `transaction_reference` before deciding to fail or confirm.
- **Duplicate Request**: The UI sends the same payment request twice. The system uses the `idempotency_key` to recognize the duplicate and returns the existing payment status.

## 2. Mandatory Jury Scenario: Payment Success + Order Service Crash

**The Problem:**
1. Inventory is reserved.
2. User pays successfully.
3. The Payment Service receives success, but the Order Service crashes before the order is created.

**The Solution:**
We DO NOT lose the successful payment, and we DO NOT ask the user to pay again.

1. **Event Publication**: Upon successful payment, the Payment Service publishes a `PaymentSucceeded` event to the Message Broker (Kafka) or a resilient Outbox table.
2. **Asynchronous Consumption**: The Order Service (when it recovers) consumes this event.
3. **Idempotent Order Creation**: The Order Service processes the event and creates the order. If the event is delivered twice, the Order Service uses the `payment_id` to ensure it only creates one order.
4. **Dead Letter Queue (DLQ)**: If order creation continuously fails (e.g., bug in the data), the message goes to a DLQ for manual reconciliation.

This guarantees that a successful charge *always* results in either a fulfilled order or an explicit, tracked refund process, but never silent data loss.
