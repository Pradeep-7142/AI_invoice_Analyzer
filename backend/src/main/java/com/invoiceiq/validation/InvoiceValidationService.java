package com.invoiceiq.validation;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.Vendor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Deterministic, AI-independent invoice validation. Every rule here is
 * fully explainable — a status and a plain-English reason — and none of it
 * depends on the extraction pipeline being right. This is what actually
 * gates {@code POST /api/invoices/{id}/verify}, not a vibe.
 *
 * <p>This is informational cross-checking, not a tax/legal authority —
 * results should be confirmed by a qualified professional, especially the
 * GSTIN format check.
 */
@Service
public class InvoiceValidationService {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final Pattern GSTIN_FORMAT = Pattern.compile("\\d{2}[A-Z]{5}\\d{4}[A-Z]\\d[Z][A-Z0-9]");

    public List<ValidationResult> validate(Invoice invoice) {
        List<ValidationResult> results = new ArrayList<>();

        validateVendorPresent(invoice, results);
        validateInvoiceNumberPresent(invoice, results);
        validateInvoiceDate(invoice, results);
        validateDueDate(invoice, results);
        validateTotalAmountPresent(invoice, results);
        validateAmountsNonNegative(invoice, results);
        validateCurrencyFormat(invoice, results);
        validateTotalsConsistent(invoice, results);
        validateLineItemsSumMatchesSubtotal(invoice, results);
        validateGstinFormat(invoice, results);

        return results;
    }

    public boolean hasBlockingErrors(List<ValidationResult> results) {
        return results.stream().anyMatch(r -> r.status() == ValidationStatus.ERROR);
    }

    private void validateVendorPresent(Invoice invoice, List<ValidationResult> results) {
        if (invoice.getVendor() == null) {
            results.add(ValidationResult.error("VENDOR_PRESENT", "Vendor is missing."));
        } else {
            results.add(ValidationResult.pass("VENDOR_PRESENT", "Vendor is set."));
        }
    }

    private void validateInvoiceNumberPresent(Invoice invoice, List<ValidationResult> results) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            results.add(ValidationResult.error("INVOICE_NUMBER_PRESENT", "Invoice number is missing."));
        } else {
            results.add(ValidationResult.pass("INVOICE_NUMBER_PRESENT", "Invoice number is set."));
        }
    }

    private void validateInvoiceDate(Invoice invoice, List<ValidationResult> results) {
        LocalDate invoiceDate = invoice.getInvoiceDate();
        if (invoiceDate == null) {
            results.add(ValidationResult.error("INVOICE_DATE_PRESENT", "Invoice date is missing."));
            return;
        }
        if (invoiceDate.isAfter(LocalDate.now())) {
            results.add(ValidationResult.warning("INVOICE_DATE_NOT_IN_FUTURE",
                "Invoice date (" + invoiceDate + ") is in the future."));
        } else {
            results.add(ValidationResult.pass("INVOICE_DATE_NOT_IN_FUTURE", "Invoice date is not in the future."));
        }
    }

    private void validateDueDate(Invoice invoice, List<ValidationResult> results) {
        if (invoice.getDueDate() == null || invoice.getInvoiceDate() == null) {
            return;
        }
        if (invoice.getDueDate().isBefore(invoice.getInvoiceDate())) {
            results.add(ValidationResult.warning("DUE_DATE_AFTER_INVOICE_DATE",
                "Due date (" + invoice.getDueDate() + ") is earlier than the invoice date (" + invoice.getInvoiceDate() + ")."));
        } else {
            results.add(ValidationResult.pass("DUE_DATE_AFTER_INVOICE_DATE", "Due date is on or after the invoice date."));
        }
    }

    private void validateTotalAmountPresent(Invoice invoice, List<ValidationResult> results) {
        if (invoice.getTotalAmount() == null) {
            results.add(ValidationResult.error("TOTAL_AMOUNT_PRESENT", "Total amount is missing."));
        } else {
            results.add(ValidationResult.pass("TOTAL_AMOUNT_PRESENT", "Total amount is set."));
        }
    }

    private void validateAmountsNonNegative(Invoice invoice, List<ValidationResult> results) {
        boolean anyNegative = Stream.of(
                invoice.getSubtotalAmount(), invoice.getTaxAmount(), invoice.getDiscountAmount(), invoice.getTotalAmount())
            .filter(a -> a != null)
            .anyMatch(a -> a.signum() < 0);
        if (anyNegative) {
            results.add(ValidationResult.error("AMOUNTS_NON_NEGATIVE", "One or more amounts are negative."));
        } else {
            results.add(ValidationResult.pass("AMOUNTS_NON_NEGATIVE", "All amounts are non-negative."));
        }
    }

    private void validateCurrencyFormat(Invoice invoice, List<ValidationResult> results) {
        String currency = invoice.getCurrency();
        if (currency == null || !CURRENCY_CODE.matcher(currency).matches()) {
            results.add(ValidationResult.error("CURRENCY_VALID", "Currency \"" + currency + "\" is not a valid 3-letter code."));
        } else {
            results.add(ValidationResult.pass("CURRENCY_VALID", "Currency code is valid."));
        }
    }

    private void validateTotalsConsistent(Invoice invoice, List<ValidationResult> results) {
        BigDecimal subtotal = invoice.getSubtotalAmount();
        BigDecimal total = invoice.getTotalAmount();
        if (subtotal == null || total == null) {
            return;
        }
        BigDecimal tax = orZero(invoice.getTaxAmount());
        BigDecimal discount = orZero(invoice.getDiscountAmount());
        BigDecimal expectedTotal = subtotal.add(tax).subtract(discount);

        if (withinTolerance(expectedTotal, total)) {
            results.add(ValidationResult.pass("TOTALS_CONSISTENT", "Subtotal, tax, and discount are consistent with the total."));
        } else {
            results.add(ValidationResult.warning("TOTALS_CONSISTENT",
                "Subtotal + tax - discount = " + expectedTotal + ", but the total is " + total + "."));
        }
    }

    private void validateLineItemsSumMatchesSubtotal(Invoice invoice, List<ValidationResult> results) {
        List<InvoiceLineItem> lineItems = invoice.getLineItems();
        if (lineItems.isEmpty()) {
            return;
        }
        BigDecimal lineItemSum = lineItems.stream()
            .map(InvoiceLineItem::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal reference = invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : invoice.getTotalAmount();
        if (reference == null) {
            return;
        }

        if (withinTolerance(lineItemSum, reference)) {
            results.add(ValidationResult.pass("LINE_ITEMS_SUM_MATCHES_SUBTOTAL", "Line item totals match the invoice subtotal."));
        } else {
            results.add(ValidationResult.warning("LINE_ITEMS_SUM_MATCHES_SUBTOTAL",
                "Line items sum to " + lineItemSum + ", but the invoice subtotal is " + reference + "."));
        }
    }

    private void validateGstinFormat(Invoice invoice, List<ValidationResult> results) {
        Vendor vendor = invoice.getVendor();
        if (vendor == null || vendor.getGstin() == null || vendor.getGstin().isBlank()) {
            return;
        }
        if (GSTIN_FORMAT.matcher(vendor.getGstin()).matches()) {
            results.add(ValidationResult.pass("GSTIN_FORMAT_VALID", "Vendor GSTIN format looks valid."));
        } else {
            results.add(ValidationResult.warning("GSTIN_FORMAT_VALID",
                "Vendor GSTIN \"" + vendor.getGstin() + "\" does not match the expected format."));
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Tolerance is the greater of 1.00 (currency unit) or 1% of the expected value, to absorb rounding. */
    private boolean withinTolerance(BigDecimal expected, BigDecimal actual) {
        BigDecimal tolerance = expected.abs().multiply(new BigDecimal("0.01")).max(BigDecimal.ONE);
        return expected.subtract(actual).abs().compareTo(tolerance) <= 0;
    }
}
