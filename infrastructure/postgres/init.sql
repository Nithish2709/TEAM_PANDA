-- SALESTORM PostgreSQL Init Script
-- This script seeds the initial flash-sale inventory on first startup.

-- ─── Inventory ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inventory (
    product_id         BIGINT PRIMARY KEY,
    total_inventory    INTEGER NOT NULL CHECK (total_inventory >= 0),
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    sold_quantity      INTEGER NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0),
    version            INTEGER NOT NULL DEFAULT 0,
    -- Invariant enforced at DB level
    CONSTRAINT chk_inventory_sum
        CHECK (available_quantity + reserved_quantity + sold_quantity = total_inventory)
);

-- ─── Inventory Reservation ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inventory_reservation (
    reservation_id  VARCHAR(64) PRIMARY KEY,
    product_id      BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    status          VARCHAR(20) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES inventory(product_id)
);
CREATE INDEX IF NOT EXISTS idx_reservation_status_expires ON inventory_reservation(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_reservation_idempotency ON inventory_reservation(idempotency_key);

-- ─── Orders ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    order_id         VARCHAR(64) PRIMARY KEY,
    customer_id      BIGINT NOT NULL,
    product_id       BIGINT NOT NULL,
    quantity         INTEGER NOT NULL,
    total_amount     NUMERIC(12,2) NOT NULL,
    reservation_id   VARCHAR(64),
    status           VARCHAR(20) NOT NULL,
    idempotency_key  VARCHAR(255) UNIQUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON orders(status);

-- ─── Payment ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment (
    payment_id             VARCHAR(64) PRIMARY KEY,
    order_id               VARCHAR(64) NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL UNIQUE,
    transaction_reference  VARCHAR(255),
    amount                 NUMERIC(12,2) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    provider               VARCHAR(50),
    failure_reason         TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
CREATE INDEX IF NOT EXISTS idx_payment_idempotency ON payment(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_payment_order ON payment(order_id);

-- ─── Seed Data ────────────────────────────────────────────────────────────────
-- Product 101: RTX 5090 Flash Sale — 100 units
INSERT INTO inventory (product_id, total_inventory, available_quantity, reserved_quantity, sold_quantity, version)
VALUES (101, 100, 100, 0, 0, 0)
ON CONFLICT (product_id) DO NOTHING;
