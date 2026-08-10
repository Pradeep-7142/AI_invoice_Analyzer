ALTER TABLE invoices
    ADD COLUMN field_confidence JSONB,
    ADD COLUMN vendor_name_raw VARCHAR(255);

ALTER TABLE invoice_documents
    ADD COLUMN extracted_text TEXT,
    ADD COLUMN ocr_confidence NUMERIC(4, 3),
    ADD COLUMN document_type VARCHAR(32),
    ADD COLUMN rejection_reason VARCHAR(500);
