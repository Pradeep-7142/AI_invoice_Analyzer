-- Baseline migration. Domain tables (organizations, users, invoices, ...)
-- are introduced in later phases (see Phase 2+ migrations) so that schema
-- history stays readable and reviewable per feature area.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
