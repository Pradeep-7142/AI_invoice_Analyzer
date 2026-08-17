package com.invoiceiq.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateDetectionServiceTest {

    private final DuplicateDetectionService service = new DuplicateDetectionService();
    private final UserAccount submittedBy = new UserAccount("a@b.com", "hash", "A B", UserRole.ROLE_EMPLOYEE);

    @Test
    void exactInvoiceNumberMatchIsFlaggedWithVeryHighProbability() {
        Invoice invoice = invoiceWith("INV-100", "5000.00", LocalDate.of(2026, 3, 1));
        Invoice existing = invoiceWith("inv-100", "9999.00", LocalDate.of(2026, 1, 1));

        List<DuplicateWarning> warnings = service.findDuplicates(invoice, List.of(existing));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).probability()).isEqualTo(0.98);
        assertThat(warnings.get(0).reason()).contains("Exact match");
    }

    @Test
    void similarAmountAndCloseDateIsFlaggedAsLikelyDuplicate() {
        Invoice invoice = invoiceWith("INV-200", "5000.00", LocalDate.of(2026, 3, 1));
        Invoice existing = invoiceWith("INV-199", "5005.00", LocalDate.of(2026, 3, 2));

        List<DuplicateWarning> warnings = service.findDuplicates(invoice, List.of(existing));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).probability()).isEqualTo(0.75);
    }

    @Test
    void similarAmountButFarApartDateIsAWeakerSignal() {
        Invoice invoice = invoiceWith("INV-300", "5000.00", LocalDate.of(2026, 3, 1));
        Invoice existing = invoiceWith("INV-301", "5005.00", LocalDate.of(2026, 1, 1));

        List<DuplicateWarning> warnings = service.findDuplicates(invoice, List.of(existing));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).probability()).isEqualTo(0.5);
    }

    @Test
    void differentAmountAndDateIsNotFlaggedAtAll() {
        Invoice invoice = invoiceWith("INV-400", "5000.00", LocalDate.of(2026, 3, 1));
        Invoice existing = invoiceWith("INV-401", "9000.00", LocalDate.of(2026, 1, 1));

        assertThat(service.findDuplicates(invoice, List.of(existing))).isEmpty();
    }

    @Test
    void resultsAreSortedByProbabilityDescending() {
        Invoice invoice = invoiceWith("INV-500", "5000.00", LocalDate.of(2026, 3, 1));
        Invoice weakMatch = invoiceWith("INV-501", "5005.00", LocalDate.of(2026, 1, 1));
        Invoice exactMatch = invoiceWith("inv-500", "1.00", LocalDate.of(2020, 1, 1));

        List<DuplicateWarning> warnings = service.findDuplicates(invoice, List.of(weakMatch, exactMatch));

        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0).probability()).isGreaterThan(warnings.get(1).probability());
    }

    private Invoice invoiceWith(String invoiceNumber, String totalAmount, LocalDate invoiceDate) {
        Invoice invoice = new Invoice(submittedBy);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(new BigDecimal(totalAmount));
        invoice.setInvoiceDate(invoiceDate);
        return invoice;
    }
}
