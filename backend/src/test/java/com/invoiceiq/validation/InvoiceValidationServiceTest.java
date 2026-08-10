package com.invoiceiq.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.Vendor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceValidationServiceTest {

    private final InvoiceValidationService service = new InvoiceValidationService();
    private final Organization organization = new Organization("Acme", "acme");
    private final UserAccount submittedBy = new UserAccount("a@b.com", "hash", "A B");

    @Test
    void freshlyUploadedInvoiceHasBlockingErrorsForEveryMissingRequiredField() {
        Invoice invoice = new Invoice(organization, submittedBy);

        List<ValidationResult> results = service.validate(invoice);

        assertThat(service.hasBlockingErrors(results)).isTrue();
        assertThat(ruleNamed(results, "VENDOR_PRESENT").status()).isEqualTo(ValidationStatus.ERROR);
        assertThat(ruleNamed(results, "INVOICE_NUMBER_PRESENT").status()).isEqualTo(ValidationStatus.ERROR);
        assertThat(ruleNamed(results, "INVOICE_DATE_PRESENT").status()).isEqualTo(ValidationStatus.ERROR);
        assertThat(ruleNamed(results, "TOTAL_AMOUNT_PRESENT").status()).isEqualTo(ValidationStatus.ERROR);
    }

    @Test
    void aFullyConsistentInvoicePassesEveryRule() {
        Invoice invoice = new Invoice(organization, submittedBy);
        invoice.setVendor(new Vendor(organization, "Adobe"));
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setCurrency("INR");
        invoice.setSubtotalAmount(new BigDecimal("1000.00"));
        invoice.setTaxAmount(new BigDecimal("180.00"));
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("1180.00"));
        invoice.replaceLineItems(List.of(
            new InvoiceLineItem(invoice, 0, "Widget", BigDecimal.ONE, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"))));

        List<ValidationResult> results = service.validate(invoice);

        assertThat(service.hasBlockingErrors(results)).isFalse();
        assertThat(results).allSatisfy(r -> assertThat(r.status()).isEqualTo(ValidationStatus.PASS));
    }

    @Test
    void futureInvoiceDateIsAWarningNotAnError() {
        Invoice invoice = minimalValidInvoice();
        invoice.setInvoiceDate(LocalDate.now().plusDays(30));

        ValidationResult result = ruleNamed(service.validate(invoice), "INVOICE_DATE_NOT_IN_FUTURE");
        assertThat(result.status()).isEqualTo(ValidationStatus.WARNING);
    }

    @Test
    void dueDateBeforeInvoiceDateIsAWarning() {
        Invoice invoice = minimalValidInvoice();
        invoice.setInvoiceDate(LocalDate.of(2026, 3, 10));
        invoice.setDueDate(LocalDate.of(2026, 3, 1));

        ValidationResult result = ruleNamed(service.validate(invoice), "DUE_DATE_AFTER_INVOICE_DATE");
        assertThat(result.status()).isEqualTo(ValidationStatus.WARNING);
    }

    @Test
    void negativeTotalIsABlockingError() {
        Invoice invoice = minimalValidInvoice();
        invoice.setTotalAmount(new BigDecimal("-50.00"));

        List<ValidationResult> results = service.validate(invoice);
        assertThat(ruleNamed(results, "AMOUNTS_NON_NEGATIVE").status()).isEqualTo(ValidationStatus.ERROR);
        assertThat(service.hasBlockingErrors(results)).isTrue();
    }

    @Test
    void malformedCurrencyIsABlockingError() {
        Invoice invoice = minimalValidInvoice();
        invoice.setCurrency("RS");

        ValidationResult result = ruleNamed(service.validate(invoice), "CURRENCY_VALID");
        assertThat(result.status()).isEqualTo(ValidationStatus.ERROR);
    }

    @Test
    void totalsInconsistentWithSubtotalTaxAndDiscountIsAWarning() {
        Invoice invoice = minimalValidInvoice();
        invoice.setSubtotalAmount(new BigDecimal("1000.00"));
        invoice.setTaxAmount(new BigDecimal("180.00"));
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("5000.00")); // way off

        ValidationResult result = ruleNamed(service.validate(invoice), "TOTALS_CONSISTENT");
        assertThat(result.status()).isEqualTo(ValidationStatus.WARNING);
    }

    @Test
    void lineItemsNotSummingToSubtotalIsAWarning() {
        Invoice invoice = minimalValidInvoice();
        invoice.setSubtotalAmount(new BigDecimal("1000.00"));
        invoice.replaceLineItems(List.of(
            new InvoiceLineItem(invoice, 0, "Widget", BigDecimal.ONE, new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"))));

        ValidationResult result = ruleNamed(service.validate(invoice), "LINE_ITEMS_SUM_MATCHES_SUBTOTAL");
        assertThat(result.status()).isEqualTo(ValidationStatus.WARNING);
    }

    @Test
    void malformedVendorGstinIsAWarningNotAnError() {
        Invoice invoice = minimalValidInvoice();
        invoice.getVendor().setGstin("NOT-A-GSTIN");

        ValidationResult result = ruleNamed(service.validate(invoice), "GSTIN_FORMAT_VALID");
        assertThat(result.status()).isEqualTo(ValidationStatus.WARNING);
    }

    private Invoice minimalValidInvoice() {
        Invoice invoice = new Invoice(organization, submittedBy);
        invoice.setVendor(new Vendor(organization, "Adobe"));
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setCurrency("INR");
        return invoice;
    }

    private ValidationResult ruleNamed(List<ValidationResult> results, String rule) {
        return results.stream().filter(r -> r.rule().equals(rule)).findFirst()
            .orElseThrow(() -> new AssertionError("No result for rule " + rule));
    }
}
