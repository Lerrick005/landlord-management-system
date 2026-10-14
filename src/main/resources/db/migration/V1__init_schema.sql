-- =============================================================================
-- V1 — Core schema
-- Tenant & Property Management System
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ROLES
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    description TEXT
);

-- -----------------------------------------------------------------------------
-- USERS
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(60)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    role_id        INT          NOT NULL REFERENCES roles(id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at  TIMESTAMPTZ
);

CREATE INDEX idx_users_role ON users(role_id);

-- -----------------------------------------------------------------------------
-- PROPERTIES
-- -----------------------------------------------------------------------------
CREATE TABLE properties (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    location      VARCHAR(180),
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    notes         TEXT,
    registered_at DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- UNITS
-- -----------------------------------------------------------------------------
CREATE TABLE units (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT        NOT NULL REFERENCES properties(id) ON DELETE RESTRICT,
    house_number    VARCHAR(30)   NOT NULL,
    unit_type       VARCHAR(30)   NOT NULL
                    CHECK (unit_type IN ('BEDSITTER','STUDIO','ONE_BEDROOM',
                                         'TWO_BEDROOM','THREE_BEDROOM','OTHER')),
    monthly_rent    NUMERIC(12,2) NOT NULL CHECK (monthly_rent >= 0),
    deposit_amount  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    status          VARCHAR(20)   NOT NULL DEFAULT 'VACANT'
                    CHECK (status IN ('VACANT','OCCUPIED','MAINTENANCE')),
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_unit_house_per_property UNIQUE (property_id, house_number)
);

CREATE INDEX idx_units_property ON units(property_id);
CREATE INDEX idx_units_status   ON units(status);

-- -----------------------------------------------------------------------------
-- TENANTS
-- -----------------------------------------------------------------------------
CREATE TABLE tenants (
    id                BIGSERIAL PRIMARY KEY,
    tenant_number     VARCHAR(20)  NOT NULL UNIQUE,     -- e.g. TEN-000124
    full_name         VARCHAR(120) NOT NULL,
    national_id       VARCHAR(40)  NOT NULL UNIQUE,
    phone             VARCHAR(30)  NOT NULL,
    alt_phone         VARCHAR(30),
    email             VARCHAR(120),
    next_of_kin       VARCHAR(120),
    next_of_kin_phone VARCHAR(30),
    occupation        VARCHAR(80),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE','LEFT')),
    registered_at     DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_name   ON tenants(full_name);

-- -----------------------------------------------------------------------------
-- TENANCIES  (tenant ↔ unit assignment)
-- -----------------------------------------------------------------------------
CREATE TABLE tenancies (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    unit_id        BIGINT        NOT NULL REFERENCES units(id)   ON DELETE RESTRICT,
    monthly_rent   NUMERIC(12,2) NOT NULL CHECK (monthly_rent >= 0),
    deposit_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    move_in_date   DATE          NOT NULL,
    move_out_date  DATE,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','ENDED')),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CHECK (move_out_date IS NULL OR move_out_date >= move_in_date)
);

CREATE INDEX idx_tenancies_tenant ON tenancies(tenant_id);
CREATE INDEX idx_tenancies_unit   ON tenancies(unit_id);
CREATE INDEX idx_tenancies_status ON tenancies(status);

-- A unit can only have ONE active tenancy at a time.
CREATE UNIQUE INDEX uq_unit_active_tenancy
    ON tenancies(unit_id) WHERE status = 'ACTIVE';

-- A tenant can only have ONE active tenancy at a time.
CREATE UNIQUE INDEX uq_tenant_active_tenancy
    ON tenancies(tenant_id) WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------------
-- PAYMENTS
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id               BIGSERIAL PRIMARY KEY,
    receipt_number   VARCHAR(30)   NOT NULL UNIQUE,   -- e.g. RCT-2026-001248
    tenant_id        BIGINT        NOT NULL REFERENCES tenants(id)   ON DELETE RESTRICT,
    tenancy_id       BIGINT        REFERENCES tenancies(id)          ON DELETE SET NULL,
    payment_date     DATE          NOT NULL DEFAULT CURRENT_DATE,
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    period_year      INT           NOT NULL CHECK (period_year BETWEEN 2000 AND 2100),
    period_month     INT           NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    method           VARCHAR(20)   NOT NULL
                     CHECK (method IN ('CASH','MPESA','BANK','OTHER')),
    reference_number VARCHAR(60),
    notes            TEXT,
    recorded_by      BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_tenant ON payments(tenant_id);
CREATE INDEX idx_payments_period ON payments(period_year, period_month);
CREATE INDEX idx_payments_date   ON payments(payment_date);

-- -----------------------------------------------------------------------------
-- AUDIT LOGS
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    username    VARCHAR(60),
    action      VARCHAR(60)  NOT NULL,
    entity      VARCHAR(60),
    entity_id   VARCHAR(60),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

-- -----------------------------------------------------------------------------
-- SYSTEM SETTINGS
-- -----------------------------------------------------------------------------
CREATE TABLE system_settings (
    key        VARCHAR(60) PRIMARY KEY,
    value      TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
