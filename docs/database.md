# SALESTORM Database Design

This document details the relational database design for the SALESTORM prototype, specifically focusing on enforcing the concurrency invariants.

## 1. Schema Overview

The database uses PostgreSQL to enforce strict ACID properties.

### Entities

1. `CUSTOMER`: Registered buyers.
2. `PRODUCT`: Product catalog information.
3. `INVENTORY`: Highly protected state of product stock.
4. `INVENTORY_RESERVATION`: Temporary holds on inventory.
5. `PAYMENT`: Records of external payment attempts and results.
6. `ORDER`: Final confirmed purchases.

## 2. Table Definitions & Constraints

### 2.1 `INVENTORY` (The Critical Table)
This table is where the core flash-sale concurrency is controlled.

```sql
CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY,
    total_inventory INT NOT NULL,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    sold_quantity INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0, -- Kept for tracking/audit, even with pessimistic locking
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Critical Database-Level Invariants
    CONSTRAINT chk_available_not_negative CHECK (available_quantity >= 0),
    CONSTRAINT chk_reserved_not_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_sold_not_negative CHECK (sold_quantity >= 0),
    CONSTRAINT chk_inventory_sum CHECK (
        (available_quantity + reserved_quantity + sold_quantity) = total_inventory
    )
);
```

### 2.2 `INVENTORY_RESERVATION`
Tracks user holds on inventory.

```sql
CREATE TABLE inventory_reservation (
    reservation_id VARCHAR(50) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'AVAILABLE', 'RESERVED', 'PAYMENT_PENDING', 'CONFIRMED', 'RELEASED', 'SOLD'
    expires_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (product_id) REFERENCES inventory(product_id)
);

CREATE INDEX idx_reservation_expires ON inventory_reservation(status, expires_at);
```

### 2.3 `PAYMENT`
Tracks interactions with the payment gateway.

```sql
CREATE TABLE payment (
    payment_id VARCHAR(50) PRIMARY KEY,
    reservation_id VARCHAR(50) NOT NULL,
    transaction_reference VARCHAR(100) UNIQUE, -- Provided by external gateway
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'PENDING', 'SUCCESS', 'FAILED', 'TIMEOUT'
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (reservation_id) REFERENCES inventory_reservation(reservation_id)
);
```

### 2.4 `ORDERS`
The final confirmed state of a purchase.

```sql
CREATE TABLE orders (
    order_id VARCHAR(50) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    reservation_id VARCHAR(50) UNIQUE NOT NULL, -- 1:1 mapping
    payment_id VARCHAR(50) UNIQUE NOT NULL,     -- 1:1 mapping ensures no duplicate orders per payment
    status VARCHAR(20) NOT NULL, -- 'CREATED', 'CONFIRMED', 'SHIPPED', 'DELIVERED'
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (reservation_id) REFERENCES inventory_reservation(reservation_id),
    FOREIGN KEY (payment_id) REFERENCES payment(payment_id)
);
```

## 3. Transaction Boundaries

- **Reservation Creation**: (1) `SELECT ... FOR UPDATE` on `inventory`. (2) `UPDATE inventory`. (3) `INSERT INTO inventory_reservation`. Commit.
- **Reservation Expiry/Release**: (1) `SELECT ... FOR UPDATE` on `inventory`. (2) `UPDATE inventory`. (3) `UPDATE inventory_reservation`. Commit.
- **Order Confirmation**: (1) `UPDATE payment`. (2) `UPDATE inventory_reservation`. (3) `SELECT ... FOR UPDATE` on `inventory`. (4) `UPDATE inventory`. (5) `INSERT INTO orders`. Commit.

By keeping these transactions strictly scoped and relying on the `chk_inventory_sum` and `chk_available_not_negative` constraints, overselling is mathematically impossible at the database layer.
