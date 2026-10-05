# SALESTORM 5-Minute Pitch Script

**Speaker Instruction:** Speak clearly, decisively, and focus entirely on engineering decisions. Do not use generic marketing fluff. Have the frontend dashboard and the terminal simulation ready.

---

## 0:00–0:30 — Problem (30 seconds)
"Good afternoon, judges. We are Team Panda, presenting SALESTORM. Our architecture tackles the hardest problem in e-commerce: the extreme contention of a flash sale. We designed a system capable of handling 10,000 customers simultaneously slamming the 'Buy Now' button for exactly 100 available units of a highly desirable product. The central engineering challenge is preventing overselling without destroying database throughput, while also keeping payment and order processing perfectly reliable under chaotic load."

## 0:30–1:00 — Requirements (30 seconds)
"To solve this, we established absolute invariants. Number one: We must guarantee zero overselling. Number two: Every critical mutation must be strictly idempotent to survive network retries and impatient users double-clicking. Number three: We must handle reservation expiries, and recover perfectly if the Order service crashes right after a payment succeeds. We prioritized correctness, reliability, and explainability above all else."

## 1:00–2:00 — Architecture (60 seconds)
*(Display HLD Diagram)*
"Our high-level architecture routes traffic through a CDN and API Gateway, stripping away basic bot abuse via rate limits. Valid traffic hits our stateless Checkout and Order services. However, because absolute consistency is our primary directive, we chose PostgreSQL as the bedrock of our Inventory Service. We rejected NoSQL because eventual consistency is unacceptable in a flash sale. For post-payment operations—like order generation and notifications—we implemented an asynchronous event-driven architecture to act as a shock absorber against traffic spikes."

## 2:00–3:00 — Critical Concurrency Design (60 seconds)
*(Display ER Diagram / Database Invariants)*
"This is the heart of our solution. We evaluated Optimistic Locking but rejected it. In a 10,000-user spike, Optimistic Locking creates a massive retry storm that starves the CPU. Instead, we use highly optimized **Pessimistic Locking** (`SELECT ... FOR UPDATE`) on the specific inventory row. We serialize the requests at the database level. 

More importantly, our safety net is mathematically enforced at the schema level. We have a strict database constraint: `available_quantity + reserved_quantity + sold_quantity = 100`. The database mathematically prevents `available_quantity` from ever dropping below zero, even if the application logic contains a bug."

## 3:00–3:45 — Payment + Order (45 seconds)
*(Display Payment Sequence Diagram)*
"Payments are chaotic. If a payment times out, we don't blindly retry; we release the inventory hold. Now, for the most critical failure scenario: What happens if a payment succeeds, but the Order Service crashes? We do not lose the order. When the payment succeeds, we use the Outbox Pattern to durably persist a `PaymentSucceeded` event in the exact same transaction. When the Order Service recovers, it consumes that event and generates the order idempotently. The user's money is never orphaned."

## 3:45–4:30 — LLD (45 seconds)
*(Briefly show Java code / IDE)*
"At the low-level, we strictly apply SOLID principles. We use a `CheckoutFacade` to orchestrate validation, but the actual state transitions are delegated to isolated modules. The entire concurrency lock is centralized in exactly one place: the `InventoryRepository`. We also inject artificial failures directly via a `FailureSimulator` component to prove our resilience."

## 4:30–5:00 — Proof (30 seconds)
*(Run the Simulation / Show Frontend Dashboard)*
"We are not just claiming this works; we built a multithreaded simulation to prove it. Watch as we blast the system with 10,000 concurrent requests. 

*(Point to output)*
As you can see: 10,000 requests. Exactly 100 successful final sales. 0 Overselling. 0 Negative Inventory. 

We are not just scaling requests. We are protecting the absolute correctness of inventory, payment, and order state under extreme contention. Thank you."
