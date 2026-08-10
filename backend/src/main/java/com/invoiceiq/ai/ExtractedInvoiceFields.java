package com.invoiceiq.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Structured fields pulled from a document's raw text, each with an
 * independent confidence score. A null field means the extractor found
 * nothing — it never guesses a value it can't support from the text.
 */
public record ExtractedInvoiceFields(
    String vendorNameRaw,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal subtotalAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    String gstin,
    Map<String, Double> confidences
) {
}
