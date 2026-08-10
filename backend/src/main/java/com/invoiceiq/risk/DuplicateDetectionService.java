package com.invoiceiq.risk;

import com.invoiceiq.entity.Invoice;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Flags likely duplicates for human review — never auto-deletes or blocks.
 * "Same vendor" is a given (callers only pass same-vendor history); this
 * layers exact-invoice-number matches and amount/date similarity on top.
 */
@Service
public class DuplicateDetectionService {

    private static final int MAX_RESULTS = 3;
    private static final double MIN_REPORTABLE_PROBABILITY = 0.5;

    public List<DuplicateWarning> findDuplicates(Invoice invoice, List<Invoice> sameVendorHistory) {
        return sameVendorHistory.stream()
            .map(other -> evaluate(invoice, other))
            .filter(w -> w != null && w.probability() >= MIN_REPORTABLE_PROBABILITY)
            .sorted(Comparator.comparingDouble(DuplicateWarning::probability).reversed())
            .limit(MAX_RESULTS)
            .toList();
    }

    private DuplicateWarning evaluate(Invoice invoice, Invoice other) {
        boolean sameInvoiceNumber = invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()
            && invoice.getInvoiceNumber().equalsIgnoreCase(other.getInvoiceNumber());

        if (sameInvoiceNumber) {
            return new DuplicateWarning(other.getId(), other.getInvoiceNumber(), 0.98,
                "Exact match on vendor and invoice number.");
        }

        boolean amountClose = amountsClose(invoice.getTotalAmount(), other.getTotalAmount());
        boolean dateClose = datesClose(invoice.getInvoiceDate(), other.getInvoiceDate());

        if (amountClose && dateClose) {
            return new DuplicateWarning(other.getId(), other.getInvoiceNumber(), 0.75,
                "Same vendor with a similar amount and date as invoice " + labelFor(other) + ".");
        }
        if (amountClose) {
            return new DuplicateWarning(other.getId(), other.getInvoiceNumber(), 0.5,
                "Same vendor with a similar amount as invoice " + labelFor(other) + ".");
        }
        return null;
    }

    private String labelFor(Invoice invoice) {
        return invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : invoice.getId().toString();
    }

    private boolean amountsClose(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        BigDecimal tolerance = a.abs().multiply(new BigDecimal("0.01")).max(BigDecimal.ONE);
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    private boolean datesClose(java.time.LocalDate a, java.time.LocalDate b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(ChronoUnit.DAYS.between(a, b)) <= 3;
    }
}
