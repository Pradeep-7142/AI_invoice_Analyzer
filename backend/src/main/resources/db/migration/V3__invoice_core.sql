CREATE TABLE vendors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    address         TEXT,
    gstin           VARCHAR(20),
    tax_id          VARCHAR(50),
    category        VARCHAR(100),
    notes           TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vendors_org ON vendors(organization_id);
CREATE INDEX idx_vendors_org_name ON vendors(organization_id, lower(name));

CREATE TABLE invoices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id),
    vendor_id             UUID REFERENCES vendors(id),
    submitted_by_user_id  UUID NOT NULL REFERENCES users(id),
    invoice_number        VARCHAR(100),
    invoice_date          DATE,
    due_date              DATE,
    currency              VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal_amount       NUMERIC(14, 2),
    tax_amount            NUMERIC(14, 2),
    discount_amount       NUMERIC(14, 2),
    total_amount          NUMERIC(14, 2),
    status                VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoices_org ON invoices(organization_id);
CREATE INDEX idx_invoices_org_status ON invoices(organization_id, status);
CREATE INDEX idx_invoices_org_vendor ON invoices(organization_id, vendor_id);
CREATE INDEX idx_invoices_org_submitter ON invoices(organization_id, submitted_by_user_id);

CREATE TABLE invoice_line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_order      INTEGER NOT NULL,
    description     VARCHAR(500) NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_line_items_invoice ON invoice_line_items(invoice_id);

CREATE TABLE invoice_documents (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id           UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    storage_key          VARCHAR(500) NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    content_type         VARCHAR(100) NOT NULL,
    file_size_bytes      BIGINT NOT NULL,
    checksum_sha256      VARCHAR(64) NOT NULL,
    processing_status    VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_documents_invoice ON invoice_documents(invoice_id);
