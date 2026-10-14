-- =============================================================================
-- V4 — Add void support to payments
-- -----------------------------------------------------------------------------
-- A payment can be marked as voided. Voided payments:
--   - are excluded from balance calculations
--   - keep their original record (audit trail)
--   - record who voided them, when, and why
-- =============================================================================

ALTER TABLE payments
    ADD COLUMN voided        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN voided_at     TIMESTAMPTZ,
    ADD COLUMN voided_by     BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN void_reason   TEXT;

-- Index for fast "only active payments" filters
CREATE INDEX idx_payments_voided ON payments(voided);
