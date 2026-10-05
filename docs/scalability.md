# SALESTORM Scalability Strategy

Scaling a flash sale requires careful balancing of edge caching, asynchronous processing, and database protection.

## 1. Traffic Management (Edge & Gateway)
- **CDN**: Static assets (images, frontend JS/CSS) are served entirely from the CDN.
- **Rate Limiting**: The API Gateway enforces strict rate limits (e.g., 5 requests/sec per IP) to block bots and abusive traffic before it reaches the application layer.

## 2. Horizontal Scaling
- The `API Gateway`, `CheckoutFacade`, and `OrderService` are entirely stateless and can be scaled horizontally behind the Load Balancer to handle the 10,000 concurrent TCP connections.

## 3. Database Bottleneck Protection (The Funnel)
Because we use Pessimistic Locking on the `Inventory` table, that specific product row is a hard bottleneck.
- **Strategy**: We let the database serialize the requests. The application threads will block.
- **Backpressure**: If the database connection pool is exhausted, the application immediately returns an `HTTP 503 Service Unavailable` or `429 Too Many Requests`. This prevents the application servers from crashing due to memory exhaustion (Out of Memory) while waiting.

## 4. Asynchronous Scaling (Post-Payment)
Once inventory is secured and payment is successful, the user's synchronous journey ends.
- **Strategy**: Order creation, inventory ledger updates, shipping APIs, and email notifications are handled asynchronously via Kafka. This allows the system to absorb a massive burst of successful payments and process the backend paperwork at a sustainable pace without impacting the user experience.
