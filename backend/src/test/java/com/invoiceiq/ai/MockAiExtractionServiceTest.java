package com.invoiceiq.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MockAiExtractionServiceTest {

    private final MockAiExtractionService service = new MockAiExtractionService();

    @Test
    void extractsAllFieldsFromAWellFormedInvoiceWithHighConfidence() {
        String text = """
            Adobe Systems Inc
            TAX INVOICE

            Invoice Number: INV-2024-88
            Invoice Date: 10/02/2026
            Due Date: 25/02/2026

            Subtotal: 4,000.00
            Tax: 500.00
            Grand Total: 4,500.00

            GSTIN: 27AAPFU0939F1ZV
            """;

        ExtractedInvoiceFields fields = service.extract(text);

        assertThat(fields.invoiceNumber()).isEqualTo("INV-2024-88");
        assertThat(fields.invoiceDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(fields.dueDate()).isEqualTo(LocalDate.of(2026, 2, 25));
        assertThat(fields.subtotalAmount()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(fields.taxAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(fields.totalAmount()).isEqualByComparingTo(new BigDecimal("4500.00"));
        assertThat(fields.gstin()).isEqualTo("27AAPFU0939F1ZV");
        assertThat(fields.vendorNameRaw()).isEqualTo("Adobe Systems Inc");

        assertThat(fields.confidences().get("invoiceNumber")).isGreaterThan(0.85);
        assertThat(fields.confidences().get("gstin")).isGreaterThan(0.9);
        assertThat(fields.confidences().get("totalAmount")).isGreaterThan(0.85);
    }

    @Test
    void neverInventsFieldsThatAreNotInTheText() {
        ExtractedInvoiceFields fields = service.extract("This document has no invoice-shaped content in it at all.");

        assertThat(fields.invoiceNumber()).isNull();
        assertThat(fields.invoiceDate()).isNull();
        assertThat(fields.totalAmount()).isNull();
        assertThat(fields.gstin()).isNull();
        assertThat(fields.confidences()).isEmpty();
    }

    @Test
    void handlesNullAndBlankTextWithoutThrowing() {
        assertThat(service.extract(null).invoiceNumber()).isNull();
        assertThat(service.extract("").totalAmount()).isNull();
    }

    @Test
    void looseInvoiceNumberPatternHasLowerConfidenceThanExplicitLabel() {
        ExtractedInvoiceFields strong = service.extract("Invoice Number: ABC-123");
        ExtractedInvoiceFields weak = service.extract("Inv: ABC-123");

        assertThat(strong.confidences().get("invoiceNumber"))
            .isGreaterThan(weak.confidences().get("invoiceNumber"));
    }
}
