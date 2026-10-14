-- =============================================================================
-- V3 — Allow "SINGLE" as a valid unit type
-- -----------------------------------------------------------------------------
-- Landlords often describe small rooms as "Singles" (distinct from Bedsitters).
-- We widen the CHECK constraint so the units table accepts it.
--
-- The "IF EXISTS" clause makes this migration safe even if the constraint
-- was previously modified outside Flyway.
-- =============================================================================

ALTER TABLE units DROP CONSTRAINT IF EXISTS units_unit_type_check;

ALTER TABLE units ADD CONSTRAINT units_unit_type_check
    CHECK (unit_type IN ('BEDSITTER','SINGLE','STUDIO','ONE_BEDROOM',
                         'TWO_BEDROOM','THREE_BEDROOM','OTHER'));
