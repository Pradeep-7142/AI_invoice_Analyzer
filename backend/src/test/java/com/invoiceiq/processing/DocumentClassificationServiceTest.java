package com.invoiceiq.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.DocumentType;
import org.junit.jupiter.api.Test;

class DocumentClassificationServiceTest {

    private final DocumentClassificationService service = new DocumentClassificationService();

    @Test
    void classifiesAClearInvoiceAsInvoiceWithHighConfidence() {
        ClassificationResult result = service.classify("""
            TAX INVOICE
            Invoice Number: INV-001
            Invoice Date: 01/01/2026
            Bill To: Acme Corp
            """);

        assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(result.confidence()).isGreaterThan(0.5);
        assertThat(service.isInvoiceCompatible(result.documentType())).isTrue();
    }

    @Test
    void classifiesAPurchaseOrderAsNotInvoiceCompatible() {
        ClassificationResult result = service.classify("""
            PURCHASE ORDER
            PO Number: PO-4471
            Ship To: Warehouse 3
            """);

        assertThat(result.documentType()).isEqualTo(DocumentType.PURCHASE_ORDER);
        assertThat(service.isInvoiceCompatible(result.documentType())).isFalse();
    }

    @Test
    void classifiesAnAccountStatementAsNotInvoiceCompatible() {
        ClassificationResult result = service.classify("""
            ACCOUNT STATEMENT
            Statement of Account for March 2026
            Opening Balance: 10,000.00
            Closing Balance: 8,200.00
            """);

        assertThat(result.documentType()).isEqualTo(DocumentType.STATEMENT);
        assertThat(service.isInvoiceCompatible(result.documentType())).isFalse();
    }

    @Test
    void textWithNoFinancialDocumentSignalIsUnknownRatherThanGuessed() {
        ClassificationResult result = service.classify("""
            Curriculum Vitae
            Objective: seeking a software engineering role.
            Work Experience: five years at various companies.
            Education: B.Tech Computer Science.
            """);

        assertThat(result.documentType()).isEqualTo(DocumentType.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void blankTextIsUnknown() {
        assertThat(service.classify("").documentType()).isEqualTo(DocumentType.UNKNOWN);
        assertThat(service.classify(null).documentType()).isEqualTo(DocumentType.UNKNOWN);
    }
}
