-- =============================================================================
-- V2 — Seed roles and default system settings
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ROLES
-- -----------------------------------------------------------------------------
INSERT INTO roles (code, name, description) VALUES
    ('ADMIN',      'Administrator',    'Full system access, including user management, backups and configuration'),
    ('MANAGER',    'Property Manager', 'Manages assigned properties, tenants, units and rent payments'),
    ('ACCOUNTANT', 'Accountant',       'Records payments, views balances and generates financial reports');

-- -----------------------------------------------------------------------------
-- DEFAULT SYSTEM SETTINGS
-- -----------------------------------------------------------------------------
-- setup.complete = false means the first-run wizard will be shown.
-- Once the admin account and organization info are created, the wizard
-- flips this to true and it is never shown again.
-- -----------------------------------------------------------------------------
INSERT INTO system_settings (key, value) VALUES
    ('org.name',              'My Property Management'),
    ('org.address',           ''),
    ('org.phone',             ''),
    ('org.email',             ''),
    ('org.currency',          'KES'),
    ('org.currency_symbol',   'KSh'),
    ('org.logo_path',         ''),
    ('receipt.prefix',        'RCT'),
    ('receipt.next_sequence', '1'),
    ('tenant.prefix',         'TEN'),
    ('tenant.next_sequence',  '1'),
    ('backup.location',       ''),
    ('setup.complete',        'false');
