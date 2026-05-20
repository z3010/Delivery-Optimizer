-- =============================================================
-- Hyperlocal Delivery Slot Optimizer — Database Schema
-- =============================================================

-- ---- Delivery Zones ----------------------------------------
CREATE TABLE IF NOT EXISTS delivery_zones (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    city            VARCHAR(100)    NOT NULL,
    latitude        DECIMAL(9,6)    NOT NULL,
    longitude       DECIMAL(9,6)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED | OVERLOADED
    max_orders_per_slot INT         NOT NULL DEFAULT 20,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_zone_status CHECK (status IN ('ACTIVE','SUSPENDED','OVERLOADED'))
);

-- ---- Fleet Capacity ----------------------------------------
-- One row per zone capturing total vehicle fleet info
CREATE TABLE IF NOT EXISTS fleet_capacity (
    id              BIGSERIAL PRIMARY KEY,
    zone_id         BIGINT          NOT NULL UNIQUE REFERENCES delivery_zones(id) ON DELETE CASCADE,
    total_vehicles  INT             NOT NULL DEFAULT 10,
    active_vehicles INT             NOT NULL DEFAULT 10,
    avg_delivery_minutes INT        NOT NULL DEFAULT 25,     -- avg time per drop
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_active_lte_total CHECK (active_vehicles <= total_vehicles)
);

-- ---- Delivery Slots ----------------------------------------
-- Each row is one 30-min window for a zone on a given date
CREATE TABLE IF NOT EXISTS delivery_slots (
    id              BIGSERIAL PRIMARY KEY,
    zone_id         BIGINT          NOT NULL REFERENCES delivery_zones(id) ON DELETE CASCADE,
    slot_date       DATE            NOT NULL,
    window_start    TIME            NOT NULL,   -- e.g. 10:00
    window_end      TIME            NOT NULL,   -- e.g. 10:30
    capacity        INT             NOT NULL,   -- max orders for this slot
    booked_count    INT             NOT NULL DEFAULT 0,
    is_available    BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_slot UNIQUE (zone_id, slot_date, window_start),
    CONSTRAINT chk_booked_lte_capacity CHECK (booked_count <= capacity)
);

-- ---- Orders -----------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              BIGSERIAL PRIMARY KEY,
    external_ref    VARCHAR(64)     NOT NULL UNIQUE,   -- caller-supplied idempotency key
    zone_id         BIGINT          NOT NULL REFERENCES delivery_zones(id),
    slot_id         BIGINT          REFERENCES delivery_slots(id),
    customer_name   VARCHAR(150)    NOT NULL,
    customer_phone  VARCHAR(20),
    delivery_address TEXT           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    placed_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    assigned_at     TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING','ALLOCATED','IN_TRANSIT','DELIVERED','FAILED'))
);

-- ---- Indexes -----------------------------------------------
CREATE INDEX IF NOT EXISTS idx_slots_zone_date   ON delivery_slots(zone_id, slot_date);
CREATE INDEX IF NOT EXISTS idx_slots_available   ON delivery_slots(is_available) WHERE is_available = TRUE;
CREATE INDEX IF NOT EXISTS idx_orders_zone       ON orders(zone_id);
CREATE INDEX IF NOT EXISTS idx_orders_slot       ON orders(slot_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_placed     ON orders(placed_at DESC);

-- ---- Auto-update updated_at --------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_zones_updated') THEN
        CREATE TRIGGER trg_zones_updated
            BEFORE UPDATE ON delivery_zones
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_slots_updated') THEN
        CREATE TRIGGER trg_slots_updated
            BEFORE UPDATE ON delivery_slots
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_fleet_updated') THEN
        CREATE TRIGGER trg_fleet_updated
            BEFORE UPDATE ON fleet_capacity
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END $$;
