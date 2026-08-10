ALTER TABLE organizations
    ADD COLUMN manager_approval_threshold NUMERIC(14, 2),
    ADD COLUMN admin_approval_threshold NUMERIC(14, 2);

ALTER TABLE invoices
    ADD COLUMN dispute_reason TEXT;

CREATE TABLE invoice_approvals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id          UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    decision            VARCHAR(16) NOT NULL,
    required_role       VARCHAR(32) NOT NULL,
    threshold_amount    NUMERIC(14, 2),
    reason              TEXT,
    decided_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_approvals_invoice ON invoice_approvals(invoice_id);
CREATE INDEX idx_invoice_approvals_org ON invoice_approvals(organization_id);

CREATE TABLE payments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    invoice_id            UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount                NUMERIC(14, 2) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    method                VARCHAR(32) NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_date        DATE NOT NULL,
    completed_at          TIMESTAMPTZ,
    reference             VARCHAR(255),
    notes                 TEXT,
    recorded_by_user_id   UUID NOT NULL REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_org ON payments(organization_id);
CREATE INDEX idx_payments_org_status ON payments(organization_id, status);

CREATE TABLE budgets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    category         VARCHAR(100) NOT NULL,
    monthly_limit    NUMERIC(14, 2) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, category)
);

CREATE INDEX idx_budgets_org ON budgets(organization_id);
