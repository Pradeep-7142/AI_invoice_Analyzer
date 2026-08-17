CREATE TABLE vendors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

CREATE INDEX idx_vendors_name ON vendors(lower(name));

CREATE TABLE invoices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id             UUID REFERENCES vendors(id) ON DELETE SET NULL,
    submitted_by_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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
    vendor_name_raw       VARCHAR(255),
    field_confidence      JSONB,
    rejection_reason      TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_vendor ON invoices(vendor_id);
CREATE INDEX idx_invoices_submitter ON invoices(submitted_by_user_id);
CREATE INDEX idx_invoices_invoice_date ON invoices(invoice_date);

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
    document_type        VARCHAR(32),
    rejection_reason     VARCHAR(500),
    extracted_text       TEXT,
    ocr_confidence       NUMERIC(4, 3),
    uploaded_by_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_documents_invoice ON invoice_documents(invoice_id);
