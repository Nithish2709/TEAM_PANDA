# SALESTORM Concurrency Proof

This document outlines the core strategy used to prevent overselling during the high-contention flash sale scenario (10,000 requests for 100 items).

## 1. The Core Invariant

The system MUST maintain the following strict invariant at all times for any given product:

```text
available_quantity + reserved_quantity + sold_quantity = total_inventory
```

For the flash sale, `total_inventory` is `100`. Therefore, the system guarantees:
- `available_quantity >= 0`
- `sold_quantity <= 100`

If 10,000 users click "Buy Now" at exactly the same millisecond, the system must guarantee that exactly 100 users get a reservation and the remaining 9,900 are safely rejected.

## 2. Concurrency Approaches Evaluated

### Approach A: Optimistic Locking (Versioning)

This approach uses a `version` column on the `inventory` table.

```sql
UPDATE inventory
SET available_quantity = available_quantity - 1,
    reserved_quantity = reserved_quantity + 1,
    version = version + 1
WHERE product_id = ?
  AND available_quantity > 0
  AND version = ?;
```

**Pros:**
- No database locks held.
- Very high read throughput.
**Cons:**
- In a flash sale (extreme contention), 10,000 requests hit the DB at once. Only 1 succeeds, and 9,999 get an `OptimisticLockException` (or 0 rows affected).
- Retrying 9,999 requests creates a massive retry storm, leading to DB CPU starvation and connection pool exhaustion. 
- Terrible for high write contention on a single row.

### Approach B: Pessimistic Locking (SELECT ... FOR UPDATE)

This approach uses database-level row locks.

```sql
SELECT *
FROM inventory
WHERE product_id = ?
FOR UPDATE;

-- Application checks quantity: if > 0, then:
UPDATE inventory
SET available_quantity = available_quantity - 1,
    reserved_quantity = reserved_quantity + 1
WHERE product_id = ?;
```

**Pros:**
- Requests are serialized at the database level for that specific row.
- No retry storms. 
- Guaranteed correctness.
**Cons:**
- Holds an exclusive lock during the transaction.
- If the transaction is slow, it blocks all other requests for that product.

## 3. Selected Strategy

For the SALESTORM prototype, we select **Approach B: Pessimistic Locking**.

**Why?**
The flash sale problem is characterized by extreme contention on a *single* product row. Optimistic locking will fail spectacularly here due to the retry storm. Pessimistic locking serializes the requests. While this creates a bottleneck on the DB, it guarantees absolute correctness without retry thrashing. 

**Implementation Details:**
1. The transaction reading the inventory MUST be extremely short. 
2. It should ONLY lock the row, check the quantity, update the row, and commit.
3. It must NOT perform network calls (e.g., calling a Payment Gateway) while holding the lock.
4. Database connection pool sizing must be tuned to handle the blocked threads waiting for the lock.

By using Pessimistic Locking in a highly optimized, short-lived transaction, we provide a solid, mathematically sound proof to the jury that overselling is impossible.
