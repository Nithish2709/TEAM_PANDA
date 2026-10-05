# SALESTORM Jury Questions & Defense Guide

This document prepares the team to answer the most critical questions from the hackathon jury during the live defense. All answers directly map to our engineered architecture.

### Q1: 10,000 users buy 100 products simultaneously. What exactly happens?
All 10,000 requests hit the API Gateway and are routed to the CheckoutFacade. They simultaneously attempt to secure a database lock (`SELECT ... FOR UPDATE`) on the specific inventory row. The database serializes these requests. The first 100 requests to acquire the lock will find `available_quantity > 0`, decrement it, and succeed. The 101st request (and all subsequent) will find `available_quantity == 0` and get an immediate `409 Conflict` (Sold Out). The connection pool absorbs the blocked threads, throwing `503` if saturated, acting as a natural backpressure mechanism.

### Q2: How do you guarantee no overselling?
We guarantee this at the absolute lowest level: the database schema. We enforce a `CHECK (available_quantity >= 0)` constraint and maintain a strict mathematical invariant: `available_quantity + reserved_quantity + sold_quantity = total_inventory`. Even if there is a bug in the Java application logic, the database will throw a constraint violation rather than allowing oversell.

### Q3: What happens if two users try to reserve the last item?
Due to our Pessimistic Locking strategy, User A's transaction secures the lock first. User B's transaction is blocked, waiting for the lock. User A decrements the inventory from 1 to 0 and commits. User B's transaction then unblocks, reads the new value (0), sees there is no stock, and safely rolls back returning a Sold Out response. 

### Q4: What happens if payment succeeds but Order Service crashes?
We do not lose the order, nor do we double-charge the user. The Payment Service publishes a `PaymentSucceeded` event to a Message Broker (or persistent Outbox). Because the Order Service is down, the message remains safely queued. When the Order Service recovers, it consumes the event and creates the order. The user's money is safe.

### Q5: What happens if payment times out?
We do not blindly retry a payment, as it risks a double charge. The system catches the timeout, marks the internal state as `TIMEOUT`, and triggers a reconciliation job to query the payment gateway later. The inventory reservation is safely expired/released in the meantime.

### Q6: What happens if the user clicks Buy twice?
The frontend generates a unique UUID (the `Idempotency-Key`) when the user enters the checkout flow. Both clicks send the identical key. The API checks this key; the first request processes normally, while the second request hits a unique constraint or fast-cache check and immediately returns the result of the first request, avoiding double reservations or double payments.

### Q7: Why SQL?
Absolute, mathematical consistency. NoSQL is faster for writes, but in a flash sale where 10,000 users fight for 1 row, strong ACID guarantees and transactional row-level locks are infinitely more valuable than raw throughput.

### Q8: Why Redis? (Optional/Conceptual)
We use Redis primarily at the API Gateway level for Distributed Rate Limiting (e.g., Token Bucket) to drop abusive bots before they reach the expensive application logic.

### Q9: Why Kafka? (Optional/Conceptual)
To decouple the synchronous, highly-stressed checkout flow from the heavy, downstream post-purchase processing (Order Creation, Warehouse syncing, Email notifications). It acts as a shock absorber.

### Q10: Why optimistic/pessimistic locking?
We chose **Pessimistic**. Optimistic locking is great for low contention, but in a flash sale, 9,999 requests would throw an OptimisticLockException. Retrying those 9,999 requests creates a catastrophic "retry storm" that burns CPU and DB connections. Pessimistic locking neatly serializes the queue.

### Q11: How does the system scale to 500k requests/sec?
The API Gateway, Checkout, and Order services are stateless and scale horizontally behind a Load Balancer. The database is the bottleneck by design. To scale further, we implement a Redis-based queue (Virtual Waiting Room) that trickles users into the database tier at exactly the rate the connection pool can handle.

### Q12: What happens when PostgreSQL goes down?
The system fails completely for new purchases. We accept this trade-off. We prefer a clean, highly-available failover to a read-replica (becoming the master) rather than degrading to a split-brain NoSQL state where we accidentally oversell inventory.

### Q13: What happens when Kafka is unavailable?
The Payment Service will fail to publish the `PaymentSucceeded` event. Because we use the Outbox Pattern, the event is saved in the same PostgreSQL database transaction as the payment update. A background worker will reliably forward it to Kafka once the broker returns.

### Q14: How are expired reservations released?
A scheduled background job (or TTL expiration on Redis) scans for reservations in the `RESERVED` state where `expires_at < NOW()`. It transitions them to `RELEASED` and increments `available_quantity` in a single atomic transaction.

### Q15: How do you detect inventory inconsistency?
By strictly adhering to the `total_inventory` invariant. We have Prometheus metrics monitoring the equation: `available_quantity + reserved_quantity + sold_quantity`. If this sum ever deviates from `total_inventory`, an immediate P1 alert fires.
