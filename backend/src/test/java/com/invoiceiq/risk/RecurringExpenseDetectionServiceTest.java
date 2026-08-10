package com.invoiceiq.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecurringExpenseDetectionServiceTest {

    private final RecurringExpenseDetectionService service = new RecurringExpenseDetectionService();
    private final Organization organization = new Organization("Acme", "acme");
    private final UserAccount submittedBy = new UserAccount("a@b.com", "hash", "A B");

    @Test
    void fewerThanThreeDatedInvoicesIsNotEnoughToCallItRecurring() {
        Invoice current = invoiceOn("2026-03-01", "999");
        List<Invoice> history = List.of(invoiceOn("2026-02-01", "999"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    @Test
    void threeConsecutiveMonthlyInvoicesOfASimilarAmountAreFlaggedAsRecurring() {
        Invoice current = invoiceOn("2026-03-01", "999");
        List<Invoice> history = List.of(invoiceOn("2026-01-01", "999"), invoiceOn("2026-02-01", "999"));

        Optional<RecurringExpenseFinding> finding = service.detect(current, history);

        assertThat(finding).isPresent();
        assertThat(finding.get().frequency()).isEqualTo("MONTHLY");
        assertThat(finding.get().occurrences()).isEqualTo(3);
        assertThat(finding.get().expectedNextDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void oneIrregularGapMeansItIsNotTreatedAsRecurringYet() {
        Invoice current = invoiceOn("2026-06-01", "999");
        List<Invoice> history = List.of(invoiceOn("2026-01-01", "999"), invoiceOn("2026-02-01", "999"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    @Test
    void anAmountFarOutsideToleranceBreaksTheRecurringPattern() {
        Invoice current = invoiceOn("2026-03-01", "5000");
        List<Invoice> history = List.of(invoiceOn("2026-01-01", "999"), invoiceOn("2026-02-01", "999"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    private Invoice invoiceOn(String date, String total) {
        Invoice invoice = new Invoice(organization, submittedBy);
        invoice.setInvoiceDate(LocalDate.parse(date));
        invoice.setTotalAmount(new BigDecimal(total));
        return invoice;
    }
}
