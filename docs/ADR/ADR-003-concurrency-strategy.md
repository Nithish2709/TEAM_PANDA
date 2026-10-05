# ADR-003 — Optimistic vs Pessimistic Concurrency for Inventory

**Status:** Accepted  
**Date:** 2026-10-05  
**Deciders:** TeamPanda

---

## Context

10,000 users will simultaneously attempt to purchase 100 units. The database row in the `inventory` table is a single shared resource. Without concurrency control, two transactions can read `available_quantity = 5`, both decrement to 4, and both commit — overselling by 1.

We evaluated two standard concurrency strategies.

---

## Approach A — Optimistic Locking (version field)

```sql
UPDATE inventory
SET available_quantity = available_quantity - 1,
    reserved_quantity  = reserved_quantity + 1,
    version            = version + 1
WHERE product_id = ?
  AND available_quantity >= ?
  AND version = ?;
```

Check `affected_rows`. If 0, another transaction won the race — retry or reject.

**Pros:**
- No lock held between read and write
- Better for low-contention workloads
- No deadlock risk

**Cons:**
- Under extreme contention (10,000 threads), the retry storm amplifies load
- High retry rate degrades P99 latency significantly
- Complex retry logic needed in the application

---

## Approach B — Pessimistic Locking (SELECT ... FOR UPDATE)

```sql
SELECT * FROM inventory WHERE product_id = ? FOR UPDATE;
-- then check and update within the same transaction
```

All threads serialise at the database. One thread holds the lock, others block. No retries needed.

**Pros:**
- Simpler application logic — no retry needed
- Under a flash-sale spike, threads queue rather than hammer
- Predictable throughput ceiling
- Zero risk of overselling — the invariant is enforced at the lock level

**Cons:**
- Lock contention becomes a bottleneck
- Lock-holder timeout/crash can stall other threads
- Does not scale horizontally without partitioning

---

## Decision

**We chose Pessimistic Locking (Approach B).**

### Why

This is a flash-sale workload — extremely high contention on a single row representing 100 units. Under optimistic locking, the thundering-herd retry behaviour would produce thousands of retries amplifying load on the DB at exactly the worst moment.

Pessimistic locking serialises the queue at the DB. The first 100 threads get units; the remaining 9,900 immediately get a readable "sold out" response once they acquire the lock. This is actually better for the customer experience (clear, fast rejection) than thousands of retries.

### Invariant

```
available_quantity + reserved_quantity + sold_quantity = total_inventory
```

This invariant is enforced at the DB level via a CHECK constraint (`init.sql`) AND by the application logic in `InventoryService.reserveInventory()`.

---

## Consequences

- `InventoryRepository.findByIdForUpdate()` uses `@Lock(PESSIMISTIC_WRITE)`
- The `version` column is retained for audit/diagnostic purposes
- Future scaling: if we partition products across shards, each product's row lives on one shard — lock contention is product-scoped, not global
- The transaction timeout must be tuned (e.g. 5s) to prevent lock starvation
