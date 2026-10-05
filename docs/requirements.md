# SALESTORM Requirements

## 1. Business Problem
Design a high-scale flash-sale e-commerce system capable of processing massive bursts of traffic safely. 

**The Critical Scenario:**
- Available inventory: 100 units
- Concurrent purchase attempts: 10,000

## 2. Core Functional Requirements
- **Inventory Protection**: The system MUST guarantee that `successful_sales <= 100`. The `available_quantity` MUST NEVER be negative.
- **Reservation System**: The system must temporarily reserve inventory during the checkout process (AVAILABLE → RESERVED).
- **Reservation Expiry**: The system must release unpaid reservations back to the available inventory pool after a timeout.
- **Idempotency**: All mutations that can be retried (reservations, checkout, payments, order creation) MUST be idempotent. A user clicking "Buy Now" twice must not result in two reservations, two payments, or two orders.
- **Order Reliability**: Orders must be created reliably. If a payment succeeds but the Order Service crashes, the system must recover and eventually create the order without losing the payment state.

## 3. Non-Functional Requirements
- **Explainability**: The architecture must be simple enough to explain in a 5-minute pitch to a jury. Correctness and explainability take precedence over raw scalability or complex microservice splitting.
- **Scalability**: The system must horizontally scale to handle the 10,000 simultaneous requests.
- **Observability**: The system must provide metrics (latency, error rates, queue backlogs) and structured logging (tracking `request_id`, `payment_id`, `order_id`).
- **Resilience**: The system must handle and recover from database failures, message broker failures, payment gateway timeouts, and downstream service failures.

## 4. Required Invariants
- `available_quantity >= 0`
- `successful_orders <= original_inventory` (e.g. 100)
- `available_quantity + reserved_quantity + sold_quantity = total_inventory`
- `overselling == 0`
- `duplicate_payments == 0`
