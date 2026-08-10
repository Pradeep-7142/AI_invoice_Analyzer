package com.invoiceiq.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnomalyDetectionServiceTest {

    private final AnomalyDetectionService service = new AnomalyDetectionService();
    private final Organization organization = new Organization("Acme", "acme");
    private final UserAccount submittedBy = new UserAccount("a@b.com", "hash", "A B");

    @Test
    void fewerThanThreePriorInvoicesMeansNoBaselineAndNoAnomaly() {
        Invoice current = invoiceWithTotal("220000");
        List<Invoice> history = List.of(invoiceWithTotal("50000"), invoiceWithTotal("52000"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    @Test
    void anAmountCloseToTheHistoricalAverageIsNotAnAnomaly() {
        Invoice current = invoiceWithTotal("50000");
        List<Invoice> history = List.of(
            invoiceWithTotal("50000"), invoiceWithTotal("52000"), invoiceWithTotal("48000"), invoiceWithTotal("51000"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    @Test
    void anAmountFarAboveTheHistoricalAverageIsFlaggedHigh() {
        Invoice current = invoiceWithTotal("220000");
        List<Invoice> history = List.of(
            invoiceWithTotal("50000"), invoiceWithTotal("52000"), invoiceWithTotal("48000"), invoiceWithTotal("51000"));

        Optional<AnomalyFinding> finding = service.detect(current, history);

        assertThat(finding).isPresent();
        assertThat(finding.get().severity()).isEqualTo("HIGH");
        assertThat(finding.get().explanation()).contains("historical average");
    }

    @Test
    void aModeratelyElevatedAmountIsFlaggedMedium() {
        Invoice current = invoiceWithTotal("54450");
        List<Invoice> history = List.of(
            invoiceWithTotal("50000"), invoiceWithTotal("52000"), invoiceWithTotal("48000"), invoiceWithTotal("51000"));

        Optional<AnomalyFinding> finding = service.detect(current, history);

        assertThat(finding).isPresent();
        assertThat(finding.get().severity()).isEqualTo("MEDIUM");
    }

    @Test
    void aCurrentInvoiceWithNoTotalAmountCannotBeCheckedForAnomalies() {
        Invoice current = new Invoice(organization, submittedBy);
        List<Invoice> history = List.of(
            invoiceWithTotal("50000"), invoiceWithTotal("52000"), invoiceWithTotal("48000"));

        assertThat(service.detect(current, history)).isEmpty();
    }

    private Invoice invoiceWithTotal(String total) {
        Invoice invoice = new Invoice(organization, submittedBy);
        invoice.setTotalAmount(new BigDecimal(total));
        return invoice;
    }
}
